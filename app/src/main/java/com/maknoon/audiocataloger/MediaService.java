package com.maknoon.audiocataloger;

import static java.lang.Thread.sleep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaService extends MediaBrowserServiceCompat implements Player.Listener
{
	private static final String TAG = "MediaService";

	private static final String MY_MEDIA_ROOT_ID = "media_root_id";
	private static final String MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id";

	private MediaSessionCompat mediaSession;

	enum STATUS {STOPPED, PAUSED, PLAYING}
	private STATUS status = STATUS.STOPPED;

	static final String Broadcast_saveFavorite = "com.maknoon.audiocataloger.saveFavorite";

	private static final long BASE_ACTIONS = PlaybackStateCompat.ACTION_PLAY_PAUSE
			| PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
			| PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_REWIND
			| PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_SEEK_TO;

	// We have tried many options to solve the issues in:
	// https://github.com/google/ExoPlayer/issues/7353
	// https://github.com/google/ExoPlayer/issues/7450
	//static OkHttpClient httpClient;
	//static OkHttpDataSourceFactory okHttpDataSourceFactory; // DataSource.Factory
	//static CronetDataSourceFactory cronetDataSourceFactory; // DataSource.Factory

	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.v(TAG, "onCreate");

		//final ComponentName mediaButtonReceiver = new ComponentName(getApplicationContext(), MediaButtonReceiver.class);
		//mediaSession = new MediaSessionCompat(this, TAG, mediaButtonReceiver, null);
		mediaSession = new MediaSessionCompat(this, TAG);

		// Enable callbacks from MediaButtons and TransportControls. No need, deprecated. Enabled by default
		//mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

		// Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
		final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP);
		//final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(BASE_ACTIONS);
		mediaSession.setPlaybackState(stateBuilder.build());

		// MediaSessionCallback() has methods that handle callbacks from a media controller. This is override (will not work) in case we use MediaControlDispatcher / MediaSessionConnector
		//mediaSession.setCallback(new MediaSessionCallback());

		// Set the session's token so that client activities can communicate with it.
		setSessionToken(mediaSession.getSessionToken());

		/*
		httpClient = new OkHttpClient.Builder()
				.eventListener(new EventListener() {
					@Override
					public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
						Log.d(TAG, "okhttp connected protocol=" + protocol);
					}

					@Override
					public void callFailed(Call call, IOException ioe) {
						Log.d(TAG, "okhttp callFailed " + ioe);
						//call.cancel();
					}
				})
				.connectTimeout(90, TimeUnit.SECONDS)
				.readTimeout(90, TimeUnit.SECONDS)
				.writeTimeout(90, TimeUnit.SECONDS)
				.retryOnConnectionFailure(false)
				.protocols(Collections.singletonList(Protocol.HTTP_1_1))
				.build();

		// require -> 'com.google.android.exoplayer:extension-okhttp:2.11.4'
		// Not useful for our SocketTimeoutException issue
		okHttpDataSourceFactory = new OkHttpDataSourceFactory(
				//httpClient.newBuilder().build(),
				httpClient,
				Util.getUserAgent(this, "Maknoon Audio Cataloger")); // R.string.app_name is arabic and causes -> Unexpected IllegalArgumentException: Unexpected char ...

		cronetDataSourceFactory = new CronetDataSourceFactory(
				new CronetEngineWrapper(this),
				Executors.newSingleThreadExecutor(),
				Util.getUserAgent(this, getString(R.string.app_name))
		);
		*/

		setUpPlayer();
	}

	static ExoPlayer mediaPlayer;
	PlayerNotificationManager playerNotificationManager;
	static boolean serviceStarted = false;

	private void setUpPlayer()
	{
		//final RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
		//.setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER); // EXTENSION_RENDERER_MODE_ON

		/*
		final DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder()
				.setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
				.setBufferDurationsMs(
						5 * 60 * 1000, // this is it!
						10 * 60 * 1000,
						DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
						DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
				)
				.setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
				.setPrioritizeTimeOverSizeThresholds(DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);

		//final TrackSelection.Factory audioTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
		//final TrackSelector trackSelector = new DefaultTrackSelector(this, audioTrackSelectionFactory);

		final DefaultTrackSelector trackSelector = new DefaultTrackSelector();
		DefaultTrackSelector.Parameters parameters = trackSelector
				.buildUponParameters()
				.setMaxAudioBitrate(16000) // TODO: change this in case we change the m4a files format
				.build();
		trackSelector.setParameters(parameters);
		*/

		//mediaPlayer = new ExoPlayer.Builder(this, renderersFactory).build();
		//mediaPlayer = new ExoPlayer.Builder(this, new MyRenderersFactory(this))
		mediaPlayer = new ExoPlayer.Builder(this)
				//.setLoadControl(builder.createDefaultLoadControl()) // controls when a MediaSource should buffer more media and how much it should buffer
				//.setTrackSelector(trackSelector)
				.build();
		mediaPlayer.addListener(this);
		mediaPlayer.setWakeMode(C.WAKE_MODE_LOCAL);
		mediaPlayer.setVolume(1.0f);

		final AudioAttributes attr = new AudioAttributes.Builder()
				.setUsage(C.USAGE_MEDIA)
				//.setContentType(C.CONTENT_TYPE_SPEECH)
				.setContentType(C.CONTENT_TYPE_MUSIC)
				.build();
		mediaPlayer.setAudioAttributes(attr, true); // In 2.9.X you don't need to manually handle audio focus (onAudioFocusChange)

		// prevents MediaSessionCallback from triggering all actions: stop/play/pause ...
		final MediaSessionConnector mediaSessionConnector = new MediaSessionConnector(mediaSession);
		mediaSessionConnector.setPlayer(mediaPlayer);
		mediaSessionConnector.setPlaybackPreparer(new MediaSessionConnectorCallback());
		/*
		mediaSessionConnector.setQueueNavigator(new TimelineQueueNavigator(mediaSession)
		{
			@Override @NonNull
			public MediaDescriptionCompat getMediaDescription(@NonNull Player player, int windowIndex)
			{
				final Bundle extras = new Bundle();
				if (mediaPlayer.isPlaying() && mediaPlayer.getDuration() > 0)
					extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration());
				else
					extras.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L);
				extras.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title);
				extras.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
				extras.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subTitle);
				extras.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1);
				extras.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1);

				final Bitmap artwork = BitmapFactory.decodeResource(getResources(), R.drawable.mk_notification_large_icon);
				return new MediaDescriptionCompat.Builder()
						.setIconBitmap(artwork)
						.setTitle(title)
						.setSubtitle(subTitle)
						.setExtras(extras)
						.build();
			}
		});
		*/

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null)
			{
				// create notification group. different concept than normal clustering of notifications
				final NotificationChannelGroup group = new NotificationChannelGroup(getString(R.string.default_notification_group_id), getString(R.string.default_notification_group_name));
				notificationManager.createNotificationChannelGroup(group);
				if (notificationManager.getNotificationChannel(getString(R.string.default_notification_channel_id)) == null)
				{
					final NotificationChannel channel = new NotificationChannel(getString(R.string.default_notification_channel_id), getString(R.string.default_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
					channel.setSound(null, null);
					channel.setGroup(getString(R.string.default_notification_group_id));
					notificationManager.createNotificationChannel(channel);
				}
			}
		}

		playerNotificationManager = new PlayerNotificationManager.Builder(
				this,
				1,
				getString(R.string.default_notification_channel_id))
				.setMediaDescriptionAdapter(new DescriptionAdapter())
				.setNotificationListener(new PlayerNotificationManager.NotificationListener()
				{
					@Override
					public void onNotificationPosted(int notificationId, @NonNull Notification notification, boolean ongoing)
					{
						if(ongoing) // play
						{
							// Avoid starting service many times. it should work without it as well since only one instance is running. However, every time you start the service, the onStartCommand() method is called.
							// Version 12, removed due to many reported 'ANRs & crashes' in google console -> Context.startForegroundService() did not then call Service.startForeground(). No value
							if(!serviceStarted)
							{
								serviceStarted = true;
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
									// In Oreo only, No effect from normal startService()
									startForegroundService(new Intent(MediaService.this, MediaService.class));
								else
									startService(new Intent(MediaService.this, MediaService.class));

								try
								{
									// Version 15, it seems the issue is with having startForeground immediately after startForegroundService in which the later didn't finish initialization.
									// startForeground should be after finish service startup in onStartCommand() but cannot put it since you cannot get the notification instance. hence implement a wait of 100ms
									sleep(100);
								}
								catch (InterruptedException e)
								{
									e.printStackTrace();
								}
							}

							// Display the notification and place the service in the foreground
							startForeground(notificationId, notification); // Version 15, moved to onStartCommand()
							LocalBroadcastManager.getInstance(MediaService.this).registerReceiver(noisyReceiver, intentFilter);
						}
						else // pause
						{
							stopForeground(false); // It works here only

							try { LocalBroadcastManager.getInstance(MediaService.this).unregisterReceiver(noisyReceiver); }
							catch (IllegalArgumentException e) { e.printStackTrace(); }
						}
					}

					@Override
					public void onNotificationCancelled(int notificationId, boolean dismissedByUser)
					{
						mediaPlayer.stop();
						mediaPlayer.clearMediaItems();
						runningUrl = "";

						/*
						if(httpClient != null)
						{
							try
							{
								httpClient.dispatcher().executorService().shutdown();
								httpClient.connectionPool().evictAll();
								final Cache cache = httpClient.cache();
								if(cache != null)
									cache.close();
							}
							catch (NullPointerException | IOException e) { e.printStackTrace(); }
							//httpClient = null;
						}
						*/

						stopForeground(true); // Take the service out of the foreground
						stopSelf();
						serviceStarted = false;

						if(dismissedByUser)
							Log.v(TAG, "User swipe the notification");

						try { LocalBroadcastManager.getInstance(MediaService.this).unregisterReceiver(noisyReceiver); }
						catch (IllegalArgumentException e) { e.printStackTrace(); }
					}
				})
				.setCustomActionReceiver(new saveFavoriteAction()).build();

		playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
		//playerNotificationManager.setUseStopAction(true); not displayed since no space available for 6 buttons. last one not displayed which is in this case stop action
		playerNotificationManager.setPlayer(mediaPlayer);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			playerNotificationManager.setSmallIcon(R.drawable.baseline_play_circle_outline_24); // does not work in kitkat -> android.app.RemoteServiceException: Bad notification posted from package *: Couldn't create icon: StatusBarIcon
		else // we didn't use the default exoplayer because it is in black color !
			playerNotificationManager.setSmallIcon(android.R.drawable.ic_media_play);
		playerNotificationManager.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
	}

	/*
	public static class CodecAudioRenderer extends MediaCodecAudioRenderer
	{
		public CodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, @Nullable AudioCapabilities audioCapabilities, AudioProcessor... audioProcessors)
		{
			super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioCapabilities, audioProcessors);
		}

		@Override
		public MediaClock getMediaClock()
		{
			Log.i(TAG, "getMediaClock:" + super.getMediaClock().getPositionUs());
			return super.getMediaClock();
		}
	}

	public static class MyRenderersFactory extends DefaultRenderersFactory
	{
		public MyRenderersFactory(Context context)
		{
			super(context);
		}

		@Override
		protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, AudioProcessor[] audioProcessors, @NonNull Handler eventHandler, @NonNull AudioRendererEventListener eventListener, ArrayList<Renderer> out)
		{
			out.add(new CodecAudioRenderer(context, MediaCodecSelector.DEFAULT, drmSessionManager, true, eventHandler, eventListener, AudioCapabilities.getCapabilities(context), audioProcessors));
		}
	}
	*/

	private class DescriptionAdapter implements PlayerNotificationManager.MediaDescriptionAdapter
	{
		@NonNull @Override
		public String getCurrentContentTitle(@NonNull Player player)
		{
			//final MediaControllerCompat controller = mediaSession.getController();
			//final MediaMetadataCompat mediaMetadata = controller.getMetadata();
			//final MediaDescriptionCompat description = mediaMetadata.getDescription(); // TODO: description = null all the time except the very first time it is triggered
			//Log.v(TAG, "description " + description);
			return title;
		}

		@Nullable @Override
		public String getCurrentContentText(@NonNull Player player)
		{
			return subTitle;
		}

		@Nullable @Override
		public Bitmap getCurrentLargeIcon(@NonNull Player player, @NonNull PlayerNotificationManager.BitmapCallback callback)
		{
			return BitmapFactory.decodeResource(getResources(), R.drawable.mk_notification_large_icon);
		}

		@Nullable @Override
		public PendingIntent createCurrentContentIntent(@NonNull Player player)
		{
			final Intent intent = new Intent(MediaService.this, MainActivity.class);
			final PendingIntent contentIntent = PendingIntent.getActivity(MediaService.this, 0, intent,
					(Util.SDK_INT >= 23) ?
							PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
							PendingIntent.FLAG_UPDATE_CURRENT);
			return contentIntent;
		}
	}

	private class saveFavoriteAction implements PlayerNotificationManager.CustomActionReceiver
	{
		@NonNull @Override
		public Map<String, NotificationCompat.Action> createCustomActions(@NonNull Context context, int instanceId)
		{
			final Intent intent = new Intent("saveFavorite").setPackage(context.getPackageName());
			final PendingIntent pendingIntent = PendingIntent.getBroadcast(
					context, instanceId, intent, (Util.SDK_INT >= 23) ?
							PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE :
							PendingIntent.FLAG_CANCEL_CURRENT);

			final NotificationCompat.Action action = new NotificationCompat.Action(
					R.drawable.outline_bookmark_24,
					"saveFavorite",
					pendingIntent);

			final Map<String, NotificationCompat.Action> actionMap = new HashMap<>();
			actionMap.put("saveFavorite", action);
			return actionMap;
		}

		@NonNull @Override
		public List<String> getCustomActions(@NonNull Player player)
		{
			final List<String> customActions = new ArrayList<>();
			customActions.add("saveFavorite");
			return customActions;
		}

		@Override
		public void onCustomAction(@NonNull Player player, @NonNull String action, @NonNull Intent intent)
		{
			// Send notification to MainActivity (if alive) to shoot saveFavorite() function
			LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(Broadcast_saveFavorite));
			//Log.v(TAG, "action2: " + intent.getAction() + action);
		}
	}

	@Override
	public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints)
	{
		// (Optional) Control the level of access for the specified package name.
		// You'll need to write your own logic to do this.
		if (TextUtils.equals(clientPackageName, getPackageName()))
		{
			// Returns a root ID that clients can use with onLoadChildren() to retrieve
			// the content hierarchy.
			return new BrowserRoot(MY_MEDIA_ROOT_ID, null);
		}
		else
		{
			// Clients can connect, but this BrowserRoot is an empty hierachy
			// so onLoadChildren returns nothing. This disables the ability to browse for content.
			return new BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null);
		}
	}

	@Override
	public void onLoadChildren(@NonNull final String parentMediaId, @NonNull final Result<List<MediaItem>> result)
	{
		//  Browsing not allowed
		if (TextUtils.equals(MY_EMPTY_MEDIA_ROOT_ID, parentMediaId))
		{
			result.sendResult(null);
			return;
		}

		// Assume for example that the music catalog is already loaded/cached.
		final List<MediaItem> mediaItems = new ArrayList<>();

		/* Check if this is the root menu:
		if (MY_MEDIA_ROOT_ID.equals(parentMediaId))
		{
			// Build the MediaItem objects for the top level,
			// and put them in the mediaItems list...
		}
		else
		{
			// Examine the passed parentMediaId to see which submenu we're at,
			// and put the children of that menu in the mediaItems list...
		}
		*/
		result.sendResult(mediaItems);
	}

	private static final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

	/*
	class MediaSessionCallback extends MediaSessionCompat.Callback
	{
		@Override
		public void onSeekTo(long pros)
		{
			super.onSeekTo(pros);
			Log.v(TAG, "onSeekTo");
			mediaPlayer.seekTo(pros);
		}

		@Override
		public void onStop()
		{
			super.onStop();
			Log.v(TAG, "onStop");
			stop();
		}

		@Override
		public void onPlay()
		{
			super.onPlay();
			Log.v(TAG, "onPlay");
			MediaService.this.play();
		}

		@Override
		public void onPause()
		{
			super.onPause();
			Log.v(TAG, "onPause");
			pause();
		}

		@Override
		public void onCommand(String command, Bundle extras, ResultReceiver cb)
		{
			super.onCommand(command, extras, cb);
			Log.v(TAG, "onCommand " + command);

			if (command != null)
				command(command, extras);
		}
	}
	*/

	class MediaSessionConnectorCallback implements MediaSessionConnector.PlaybackPreparer
	{
		@Override
		public void onPrepare(boolean playWhenReady)
		{
			Log.v(TAG, "onPrepare");
		}

		@Override
		public void onPrepareFromMediaId(@NonNull String mediaId, boolean playWhenReady, Bundle extras)
		{
		}

		@Override
		public void onPrepareFromSearch(@NonNull String query, boolean playWhenReady, Bundle extras)
		{
		}

		@Override
		public void onPrepareFromUri(@NonNull Uri uri, boolean playWhenReady, Bundle extras)
		{
		}

		@Override
		public long getSupportedPrepareActions()
		{
			long actions = PlaybackStateCompat.ACTION_PLAY
					| PlaybackStateCompat.ACTION_PLAY_PAUSE
					| PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SEEK_TO;
			if (mediaPlayer.getPlayWhenReady())
				actions |= PlaybackStateCompat.ACTION_PAUSE;
			return actions;
		}

		@Override
		public boolean onCommand(@NonNull Player player, @NonNull String command, @Nullable Bundle extras, @Nullable ResultReceiver cb)
		{
			command(command, extras);
			Log.v(TAG, "MediaSessionConnectorCallback onCommand " + command);
			return true;
		}
	}

	String title, subTitle, runningUrl = "";
	void command(String command, Bundle extras)
	{
		if (command.equals("play"))
		{
			title = "\u200e" + extras.getString("title"); // we put LTR mark since subTitle cannot be changed to RTL using Mark '\u200f'
			subTitle = "\u200e" + extras.getString("subTitle");
			final String url = extras.getString("url");
			final int offset = extras.getInt("offset");
			//final int duration = extras.getInt("duration"); // It should be used to stop the playing after period = duration

			//setMediaSessionMetadata(title, subTitle);
			exo_play(url, offset);
		}
	}

	void mp_play(String url, int offset)
	{
		//mediaSession.getController().getTransportControls().stop(); This shoot a thread that will not wait until it finish before proceeding to the next code
		mp_stop();
		if (mediaPlayer != null)
		{
			// Produces DataSource instances through which media data is loaded
			final DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

			// This is the MediaSource representing the media to be played
			final MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
					.createMediaSource(com.google.android.exoplayer2.MediaItem.fromUri(Uri.parse(url)));

			if (offset != 0)
			{
				mediaPlayer.seekTo(offset);
				mediaPlayer.setMediaSource(videoSource, false);
			}
			else
				// Prepare the player with the source
				mediaPlayer.setMediaSource(videoSource, true);

			mediaPlayer.prepare();
			mediaPlayer.setPlayWhenReady(true);
		}
	}

	void exo_play(String url, int offset)
	{
		if (runningUrl.equals(url) && mediaPlayer.getPlaybackState() == Player.STATE_READY)
		{
			mediaPlayer.seekTo(offset);
			mediaPlayer.setPlayWhenReady(true);
		}
		else
		{
			playerNotificationManager.setPlayer(null);

			/*
			final TransferListener transferListener = new TransferListener()
			{
				@Override
				public void onTransferInitializing(DataSource dataSource, DataSpec dataSpec, boolean isNetwork)
				{
					Log.v(TAG, "OK initializing transfer for " + dataSpec.getHttpMethodString() + " " + dataSpec.uri + ", " + dataSpec.httpRequestHeaders);
				}

				@Override
				public void onTransferStart(DataSource dataSource, DataSpec dataSpec, boolean b)
				{
				}

				@Override
				public void onBytesTransferred(DataSource dataSource, DataSpec dataSpec, boolean b, int i)
				{
				}

				@Override
				public void onTransferEnd(DataSource dataSource, DataSpec dataSpec, boolean b)
				{
				}
			};

			// Produces DataSource instances through which media data is loaded
			//final DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory(
			final DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(
					Util.getUserAgent(this, getString(R.string.app_name)),
					transferListener,
					DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, true); // Both timeout is 8000

			final DataSource.Factory mDataSourceFactory = new DefaultDataSourceFactory(
					this,
					new CronetDataSourceFactory(
							new CronetEngineWrapper(this),
							Executors.newSingleThreadExecutor(),
							Util.getUserAgent(this, getString(R.string.app_name))));

			// Not needed. Only useful for none seekable mp3 files
			// https://exoplayer.dev/progressive.html#enabling-constant-bitrate-seeking
			//final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

			// This is the MediaSource representing the media to be played. MediaSource can not be reused
			final ProgressiveMediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory) // okHttpDataSourceFactory
					// Useless, no value, still getting java.net.SocketTimeoutException: timeout
					.setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy()
					{
						@Override
						public int getMinimumLoadableRetryCount(int dataType)
						{
							return 0;
							/*
							if(dataType == C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE)
								return DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE; // 6
							else
								return DEFAULT_MIN_LOADABLE_RETRY_COUNT; // 3
							* /
						}
					})
					.createMediaSource(Uri.parse(url));
					*/

			// Remove IcyHeaders since streaming is stopped after 5-6 times
			// https://github.com/google/ExoPlayer/issues/7450
			// TODO: retest in later versions without this. it might be solved
			final ProgressiveMediaSource audioSource2 = new ProgressiveMediaSource.Factory(() ->
			{
				if (url.startsWith("http"))
				{
					final HttpDataSource dataSource = new DefaultHttpDataSource(Util.getUserAgent(this, getString(R.string.app_name)))
					{
						@Override
						public long open(DataSpec dataSpec) throws HttpDataSourceException
						{
							final Map<String, String> m1 = dataSpec.httpRequestHeaders;
							final Map<String, String> m2 = new HashMap<>();
							for (Map.Entry<String, String> entry : m1.entrySet())
							{
								if (!entry.getKey().equals(IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME))
									m2.put(entry.getKey(), entry.getValue());
							}

							//dataSpec.httpRequestHeaders.remove(IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME);
							return super.open(dataSpec.withRequestHeaders(m2));
						}
					};

					//dataSource.clearRequestProperty(IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME);
					return dataSource;
				}
				else
					//return new DefaultDataSource(this, Util.getUserAgent(this, getString(R.string.app_name)), false);
					return new FileDataSource();

			}).createMediaSource(com.google.android.exoplayer2.MediaItem.fromUri(Uri.parse(url)));

			if (offset != 0)
			{
				mediaPlayer.seekTo(offset);
				mediaPlayer.setMediaSource(audioSource2, false);
			}
			else
				// Prepare the player with the source
				mediaPlayer.setMediaSource(audioSource2, true);

			mediaPlayer.prepare();
			runningUrl = url;
			mediaPlayer.setPlayWhenReady(true);
			playerNotificationManager.setPlayer(mediaPlayer);
		}
	}

	void mp_play()
	{
		Log.v(TAG, "mp_play()");

		status = STATUS.PLAYING;

		final PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();

		// you must call setActions() to say exactly what actions you support. if you don't set that then you won't get any of the callbacks relating to media buttons.
		playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP);
		playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f, SystemClock.elapsedRealtime());
		mediaSession.setPlaybackState(playbackStateBuilder.build());

		// Set the session active (and update metadata and state)
		// You must set the session to active before it can start receiving media button events or transport commands
		mediaSession.setActive(true);
		mediaPlayer.setPlayWhenReady(true); // to resume from pause

		// Register BECOME_NOISY BroadcastReceiver
		//Handles headphones coming unplugged. cannot be done through a manifest receiver
		LocalBroadcastManager.getInstance(MediaService.this).registerReceiver(noisyReceiver, intentFilter);

		/*
		final NotificationCompat.Builder builder = MediaStyleHelper.from(MediaService.this, mediaSession);
		builder.addAction(new NotificationCompat.Action(R.drawable.outline_pause_24, "توقف", MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_PAUSE)));
		final Notification notification = builder.build();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null)
			{
				// create notification group. different concept than normal clustering of notifications
				final NotificationChannelGroup group = new NotificationChannelGroup(getString(R.string.default_notification_group_id), getString(R.string.default_notification_group_name));
				notificationManager.createNotificationChannelGroup(group);

				if (notificationManager.getNotificationChannel(getString(R.string.default_notification_channel_id)) == null)
				{
					final NotificationChannel channel = new NotificationChannel(getString(R.string.default_notification_channel_id), getString(R.string.default_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
					channel.setSound(null, null);
					channel.setGroup(getString(R.string.default_notification_group_id));
					notificationManager.createNotificationChannel(channel);
				}

				// In Oreo only, No effect from normal startService()
				startForegroundService(new Intent(this, MediaService.class));

				// Display the notification and place the service in the foreground
				startForeground(1, notification);
			}
		}
		else
		{
			startService(new Intent(this, MediaService.class));
			startForeground(1, notification);
		}
		*/
	}

	void mp_stop()
	{
		Log.v(TAG, "mp_stop()");

		status = STATUS.STOPPED;

		// Set the session inactive  (and update metadata and state)
		mediaSession.setActive(false);

		if (mediaPlayer != null)
		{
			if(mediaPlayer.isPlaying())
				mediaPlayer.stop();
			mediaPlayer.setPlayWhenReady(false);
		}

		final PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
		playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP);
		playbackStateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f, SystemClock.elapsedRealtime());
		mediaSession.setPlaybackState(playbackStateBuilder.build());

		// try-catch to avoid crashing the app in case it is not registered as for the first time for example
		try
		{
			// unregister BECOME_NOISY BroadcastReceiver
			LocalBroadcastManager.getInstance(MediaService.this).unregisterReceiver(noisyReceiver);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
		}

		// Take the service out of the foreground
		stopForeground(true);
		stopSelf();
		serviceStarted = false;
	}

	void mp_pause()
	{
		Log.v(TAG, "mp_pause()");

		status = STATUS.PAUSED;

		// Update metadata and state
		mediaPlayer.setPlayWhenReady(false); // pause

		final PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
		playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP);
		playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f, SystemClock.elapsedRealtime());
		mediaSession.setPlaybackState(playbackStateBuilder.build());

		// Take the service out of the foreground, retain the notification
		stopForeground(false);

		/*
		final NotificationCompat.Builder builder = MediaStyleHelper.from(MediaService.this, mediaSession);
		builder.addAction(new NotificationCompat.Action(R.drawable.baseline_play_arrow_24, "تشغيل", MediaButtonReceiver.buildMediaButtonPendingIntent(MediaService.this, PlaybackStateCompat.ACTION_PLAY)));
		final Notification notification = builder.build();
		NotificationManagerCompat.from(MediaService.this).notify(1, notification);
		*/

		try
		{
			// unregister BECOME_NOISY BroadcastReceiver
			LocalBroadcastManager.getInstance(MediaService.this).unregisterReceiver(noisyReceiver);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
		}
	}

	private void setMediaSessionMetadata(final String title, final String subTitle)
	{
		final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

		//Notification icon in card
		//metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, BitmapFactory.decodeResource(getResources(), R.drawable.mk_notification_large_icon));
		//metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(getResources(), R.drawable.mk_notification_large_icon));

		//lock screen icon for pre lollipop
		//metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), R.drawable.mk_notification_large_icon));

		metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title);
		metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
		metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subTitle);
		//metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, subTitle);
		metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1);
		metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1);

		Log.e(TAG, "mediaPlayer.getDuration(): " + mediaPlayer.getDuration());

		if(mediaPlayer.isPlaying() && mediaPlayer.getDuration() > 0)
			metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration());
		else
			metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L);

		mediaSession.setMetadata(metadataBuilder.build());
	}

	private final BroadcastReceiver noisyReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			mediaSession.getController().getTransportControls().pause();
		}
	};

	@Override
	public void onPlayerError(PlaybackException error)
	{
		Toast.makeText(this, "حدث خطأ أثناء تحميل المادة، تأكد من اتصالك بالانترنت وأعد المحاولة", Toast.LENGTH_LONG).show();

		final Throwable cause = error.getCause();
		if (cause instanceof HttpDataSourceException)
		{
			// An HTTP error occurred.
			final HttpDataSourceException httpError = (HttpDataSourceException) cause;

			// This is the request for which the error occurred.
			final DataSpec requestDataSpec = httpError.dataSpec;
			Log.e(TAG, "httpError.dataSpec: " + requestDataSpec);

			// It's possible to find out more about the error both by casting and by
			// querying the cause.
			if (httpError instanceof HttpDataSource.InvalidResponseCodeException)
			{
				// Cast to InvalidResponseCodeException and retrieve the response code,
				// message and headers.

				final HttpDataSource.InvalidResponseCodeException ex = (HttpDataSource.InvalidResponseCodeException) httpError;
				Log.e(TAG, "Http error during playback: " + ex.responseCode);

				final Throwable innerCause = httpError.getCause();
				if (innerCause != null)
					Log.e(TAG, "innerCause.getMessage(): " + innerCause.getMessage());
			}
			else
			{
				// Try calling httpError.getCause() to retrieve the underlying cause,
				// although note that it may be null.
				Log.e(TAG, "httpError.getCause(): " + httpError.getCause());
			}
		}
		else
			Log.e(TAG, "Error: " + error);

		playerNotificationManager.setPlayer(null); // will call onNotificationCancelled()
	}

	@Override
	public void onPlaybackStateChanged(int playbackState)
	//public void onPlayerStateChanged(boolean playWhenReady, int playbackState)
	{
		/* This is useful in case you do NOT use PlayerNotificationManager. the sync (play/pause) between normal notification and PlayerControlView is missing unless you use PlayerNotificationManager
		// also all needed updates is done now in onNotificationPosted()

		Log.v(TAG, "onPlayerStateChanged " + playWhenReady);
		if (playWhenReady && playbackState == Player.STATE_READY)
		{
			// Moved to onIsPlayingChanged
			//mp_play();
		}
		else
		{
			if (playWhenReady)
			{
				// might be idle (plays after prepare()),
				// buffering (plays when data available)
				// or ended (plays when seek away from end)
			}
			else
			{
				// player paused in any state
				if(status == STATUS.PLAYING)
					mp_pause();
					//mediaSession.getController().getTransportControls().pause(); // Should not do this since this will trigger dispatchSetPlayWhenReady
			}
		}
		*/

		switch (playbackState)
		{
			case ExoPlayer.STATE_IDLE:
				Log.v(TAG, "onPlayerStateChanged -> ExoPlayer.STATE_IDLE");
				break;
			case ExoPlayer.STATE_BUFFERING:
				Log.v(TAG, "onPlayerStateChanged -> ExoPlayer.STATE_BUFFERING");
				break;
			case ExoPlayer.STATE_READY:
				Log.v(TAG, "onPlayerStateChanged -> ExoPlayer.STATE_READY");
				break;
			case ExoPlayer.STATE_ENDED:
				Log.v(TAG, "onPlayerStateChanged -> ExoPlayer.STATE_ENDED");
				break;
		}
	}

	/* This is useful in case you do NOT use PlayerNotificationManager. the sync (play/pause) between normal notification and PlayerControlView is missing unless you use PlayerNotificationManager
	// also all needed updates is done now in onNotificationPosted()
	@Override
	public void onIsPlayingChanged(boolean isPlaying)
	{
		Log.v(TAG, "onIsPlayingChanged " + isPlaying);
		if (isPlaying)
		{
			// Active playback.
			/*
			if(status != STATUS.PLAYING)
				//mediaSession.getController().getTransportControls().play();
				play();
			 *

			// Register BECOME_NOISY BroadcastReceiver
			//Handles headphones coming unplugged. cannot be done through a manifest receiver
			LocalBroadcastManager.getInstance(MediaService.this).registerReceiver(noisyReceiver, intentFilter);
		}
		else
		{
			// Not playing because playback is paused, ended, suppressed, or the player
			// is buffering, stopped or failed. Check player.getPlaybackState,
			// player.getPlayWhenReady, player.getPlaybackError and
			// player.getPlaybackSuppressionReason for details.
		}
	}
	*/

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.v(TAG, "onStartCommand " + intent);

		if(intent != null && mediaSession != null)
			MediaButtonReceiver.handleIntent(mediaSession, intent);

		//return START_STICKY; // Service will be restarted if killed by OS. In this case it will cause a crash since intent = null and our MediaService start the service when only called by play(,,) which is not done when restarted by OS
		return START_NOT_STICKY;
		//return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy()
	{
		Log.v(TAG, "onDestroy");

		//mediaSession.getController().getTransportControls().stop(); This shoot a thread that will not wait until it finish before proceeding to the next code
		//mp_stop();

		if (mediaPlayer != null)
		{
			playerNotificationManager.setPlayer(null); // will call onNotificationCancelled()
			mediaPlayer.release();
			mediaPlayer = null;
		}

		mediaSession.release();
		super.onDestroy();
	}
}