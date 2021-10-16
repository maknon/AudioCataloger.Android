package com.maknoon.audiocataloger;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;

public class MediaStyleHelper
{
	public static NotificationCompat.Builder from(final Context context, final MediaSessionCompat mediaSession)
	{
		final Intent intent = new Intent(context, MainActivity.class);
		final PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		final MediaControllerCompat controller = mediaSession.getController();
		final MediaMetadataCompat mediaMetadata = controller.getMetadata();
		final MediaDescriptionCompat description = mediaMetadata.getDescription();
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.default_notification_channel_id));
		builder
				.setContentTitle(description.getTitle())
				.setContentText(description.getSubtitle())
				//.setSubText(description.getDescription())
				//.setLargeIcon(description.getIconBitmap())
				.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher_round))
				// Enable launching the player by clicking the notification
				.setContentIntent(contentIntent)

				// Stop the service when the notification is swiped away
				.setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP))

				// Make the transport controls visible on the lock screen
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setSmallIcon(R.drawable.baseline_play_circle_outline_24)
				.setColor(ContextCompat.getColor(context, R.color.colorPrimaryDark))
				.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
						.setMediaSession(mediaSession.getSessionToken())
						.setShowActionsInCompactView(0)
						.setShowCancelButton(true)
						.setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context,
								PlaybackStateCompat.ACTION_STOP)));
		return builder;
	}
}