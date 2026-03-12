const { onRequest } = require("firebase-functions/v2/https");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const { google } = require("googleapis");

admin.initializeApp();
const db = admin.firestore();

function getOAuth2Client(accessToken) {
  const oauth2Client = new google.auth.OAuth2();
  oauth2Client.setCredentials({ access_token: accessToken });
  return oauth2Client;
}

/**
 * registerCalendarWatch - Callable function
 */
exports.registerCalendarWatch = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be authenticated");
    }

    const uid = request.auth.uid;
    const { accessToken } = request.data;

    if (!accessToken) {
      throw new HttpsError("invalid-argument", "accessToken is required");
    }

    const calendar = google.calendar({
      version: "v3",
      auth: getOAuth2Client(accessToken),
    });

    const channelId = `cal-watch-${uid}-${Date.now()}`;
    const webhookUrl = process.env.WEBHOOK_URL ||
      `https://asia-northeast3-calender-share-be7d2.cloudfunctions.net/calendarWebhook`;

    try {
      const watchResponse = await calendar.events.watch({
        calendarId: "primary",
        requestBody: {
          id: channelId,
          type: "web_hook",
          address: webhookUrl,
          params: { ttl: "604800" },
        },
      });

      const { resourceId, expiration } = watchResponse.data;

      await db.collection("calendarWatches").doc(uid).set({
        channelId,
        resourceId,
        expiration: Number(expiration),
        uid,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      await db.collection("userTokens").doc(uid).set(
        { accessToken, updatedAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true }
      );

      // Also store user's email for event sync
      const userRecord = await admin.auth().getUser(uid);
      await db.collection("userTokens").doc(uid).set(
        { email: userRecord.email || "" },
        { merge: true }
      );

      console.log(`Watch registered for user ${uid}, channel: ${channelId}`);
      return { channelId, expiration: Number(expiration) };
    } catch (error) {
      console.error("Failed to register watch:", error.message);
      throw new HttpsError("internal", `Failed to register calendar watch: ${error.message}`);
    }
  }
);

/**
 * Helper: parse Google Calendar event time to epoch millis
 */
function parseEventTime(eventDateTime) {
  if (!eventDateTime) return 0;
  const dateTimeStr = eventDateTime.dateTime || eventDateTime.date;
  if (!dateTimeStr) return 0;
  return new Date(dateTimeStr).getTime();
}

/**
 * calendarWebhook - HTTP endpoint
 * Syncs events to the same "shared_events" collection the Android app uses.
 */
exports.calendarWebhook = onRequest(
  { region: "asia-northeast3" },
  async (req, res) => {
    const channelId = req.headers["x-goog-channel-id"];
    const resourceState = req.headers["x-goog-resource-state"];

    console.log(`Webhook received: channel=${channelId}, state=${resourceState}`);

    if (resourceState === "sync") {
      res.status(200).send("OK");
      return;
    }

    if (!channelId) {
      res.status(400).send("Missing channel ID");
      return;
    }

    try {
      const watchSnapshot = await db
        .collection("calendarWatches")
        .where("channelId", "==", channelId)
        .limit(1)
        .get();

      if (watchSnapshot.empty) {
        console.warn(`No watch found for channel: ${channelId}`);
        res.status(200).send("OK");
        return;
      }

      const watchDoc = watchSnapshot.docs[0];
      const { uid } = watchDoc.data();

      const tokenDoc = await db.collection("userTokens").doc(uid).get();
      if (!tokenDoc.exists) {
        console.warn(`No token found for user: ${uid}`);
        res.status(200).send("OK");
        return;
      }

      const { accessToken, email } = tokenDoc.data();
      const ownerEmail = email || "";

      const calendar = google.calendar({
        version: "v3",
        auth: getOAuth2Client(accessToken),
      });

      const now = new Date();
      const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      const sixtyDaysLater = new Date(now.getTime() + 60 * 24 * 60 * 60 * 1000);

      const eventsResponse = await calendar.events.list({
        calendarId: "primary",
        timeMin: thirtyDaysAgo.toISOString(),
        timeMax: sixtyDaysLater.toISOString(),
        singleEvents: true,
        orderBy: "startTime",
        maxResults: 250,
      });

      const events = eventsResponse.data.items || [];

      // Delete old events for this user from shared_events collection
      const existingEvents = await db.collection("shared_events")
        .where("ownerEmail", "==", ownerEmail)
        .get();

      const batch = db.batch();
      existingEvents.forEach((doc) => batch.delete(doc.ref));

      // Add current events (same format as Android app's syncEventsToFirestore)
      for (const event of events) {
        const startTime = parseEventTime(event.start);
        const endTime = parseEventTime(event.end);
        const isAllDay = !event.start?.dateTime;
        const timeZone = event.start?.timeZone || event.end?.timeZone || "Asia/Seoul";

        const docRef = db.collection("shared_events").doc(`${uid}_${event.id}`);
        batch.set(docRef, {
          id: event.id || "",
          title: event.summary || "(제목 없음)",
          description: event.description || "",
          startTime,
          endTime,
          location: event.location || "",
          isAllDay,
          calendarId: "primary",
          ownerEmail,
          color: "#4285F4",
          timeZone,
        });
      }

      await batch.commit();
      console.log(`Synced ${events.length} events for user ${uid} (${ownerEmail})`);

      await notifySubscribers(uid);

      res.status(200).send("OK");
    } catch (error) {
      console.error("Webhook processing error:", error.message);
      res.status(200).send("OK");
    }
  }
);

/**
 * Notify subscribers via FCM
 */
async function notifySubscribers(ownerUid) {
  try {
    const sharedSnapshot = await db
      .collection("shared_calendars")
      .doc(ownerUid)
      .get();

    if (!sharedSnapshot.exists) return;

    const data = sharedSnapshot.data();
    const subscriberIds = data?.subscriberUids || [];

    for (const subscriberId of subscriberIds) {
      const subscriberDoc = await db.collection("users").doc(subscriberId).get();
      if (!subscriberDoc.exists) continue;

      const fcmToken = subscriberDoc.data()?.fcmToken;
      if (!fcmToken) continue;

      try {
        await admin.messaging().send({
          token: fcmToken,
          notification: {
            title: "캘린더 업데이트",
            body: "공유받은 캘린더에 새로운 변경사항이 있습니다.",
          },
          data: { type: "calendar_update", ownerUid },
        });
      } catch (msgError) {
        console.warn(`Failed to send FCM to ${subscriberId}:`, msgError.message);
      }
    }
  } catch (error) {
    console.error("Error notifying subscribers:", error.message);
  }
}

/**
 * renewCalendarWatches - Scheduled function
 */
exports.renewCalendarWatches = onSchedule(
  {
    schedule: "every 24 hours",
    region: "asia-northeast3",
    timeZone: "Asia/Seoul",
  },
  async () => {
    const twoDaysFromNow = Date.now() + 2 * 24 * 60 * 60 * 1000;

    const expiringWatches = await db
      .collection("calendarWatches")
      .where("expiration", "<", twoDaysFromNow)
      .get();

    console.log(`Found ${expiringWatches.size} expiring watches to renew`);

    for (const doc of expiringWatches.docs) {
      const { uid, channelId, resourceId } = doc.data();

      try {
        const tokenDoc = await db.collection("userTokens").doc(uid).get();
        if (!tokenDoc.exists) {
          console.warn(`No token for user ${uid}, removing watch`);
          await doc.ref.delete();
          continue;
        }

        const { accessToken } = tokenDoc.data();
        const calendar = google.calendar({
          version: "v3",
          auth: getOAuth2Client(accessToken),
        });

        try {
          await calendar.channels.stop({
            requestBody: { id: channelId, resourceId },
          });
        } catch (stopError) {
          console.warn(`Could not stop old channel ${channelId}:`, stopError.message);
        }

        const newChannelId = `cal-watch-${uid}-${Date.now()}`;
        const webhookUrl = process.env.WEBHOOK_URL ||
          `https://asia-northeast3-calender-share-be7d2.cloudfunctions.net/calendarWebhook`;

        const watchResponse = await calendar.events.watch({
          calendarId: "primary",
          requestBody: {
            id: newChannelId,
            type: "web_hook",
            address: webhookUrl,
            params: { ttl: "604800" },
          },
        });

        await doc.ref.set({
          channelId: newChannelId,
          resourceId: watchResponse.data.resourceId,
          expiration: Number(watchResponse.data.expiration),
          uid,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        console.log(`Renewed watch for user ${uid}`);
      } catch (error) {
        console.error(`Failed to renew watch for user ${uid}:`, error.message);
      }
    }
  }
);

/**
 * updateAccessToken - Callable function
 */
exports.updateAccessToken = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be authenticated");
    }

    const uid = request.auth.uid;
    const { accessToken } = request.data;

    if (!accessToken) {
      throw new HttpsError("invalid-argument", "accessToken is required");
    }

    await db.collection("userTokens").doc(uid).set(
      { accessToken, updatedAt: admin.firestore.FieldValue.serverTimestamp() },
      { merge: true }
    );

    return { success: true };
  }
);
