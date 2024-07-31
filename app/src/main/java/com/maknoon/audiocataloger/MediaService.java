package com.maknoon.audiocataloger;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class MediaService extends MediaSessionService implements Player.Listener
{
	private static final String TAG = "MediaService";

	private MediaSession mediaSession;

	static final String Broadcast_saveFavorite = "com.maknoon.audiocataloger.saveFavorite";

	// We have tried many options to solve the issues in:
	// https://github.com/google/ExoPlayer/issues/7353
	// https://github.com/google/ExoPlayer/issues/7450
	//static OkHttpClient httpClient;
	//static OkHttpDataSourceFactory okHttpDataSourceFactory; // DataSource.Factory
	//static CronetDataSourceFactory cronetDataSourceFactory; // DataSource.Factory

	ForwardingPlayer mediaPlayer;

	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.v(TAG, "onCreate");

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
				.setMaxAudioBitrate(16000) // change this in case we change the m4a files format
				.build();
		trackSelector.setParameters(parameters);
		*/

		final AudioAttributes attr = new AudioAttributes.Builder()
				.setUsage(C.USAGE_MEDIA)
				.setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
				.build();

		//mediaPlayer = new ExoPlayer.Builder(this, renderersFactory).build();
		//mediaPlayer = new ExoPlayer.Builder(this, new MyRenderersFactory(this))
		final ExoPlayer player = new ExoPlayer.Builder(this)
				.setAudioAttributes(attr, true)  // In 2.9.X you don't need to manually handle audio focus (onAudioFocusChange)
				//.setLoadControl(builder.createDefaultLoadControl()) // controls when a MediaSource should buffer more media and how much it should buffer
				//.setTrackSelector(trackSelector)
				.build();

		player.setWakeMode(C.WAKE_MODE_NETWORK);
		player.setHandleAudioBecomingNoisy(true);
		//player.getAvailableCommands().buildUpon().remove(COMMAND_SEEK_TO_NEXT).build(); // not working, replaced with overriding the ForwardingPlayer

		mediaPlayer = new ForwardingPlayer(player)
		{
			// https://github.com/androidx/media/issues/140
			@NonNull
			@Override
			public Player.Commands getAvailableCommands()
			{
				return super.getAvailableCommands()
						.buildUpon()
						.remove(Player.COMMAND_SEEK_TO_NEXT)
						.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
						.build();
			}

			@Override
			public boolean isCommandAvailable(int command)
			{
				if (command == Player.COMMAND_SEEK_TO_NEXT)
					return false;
				if (command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
					return false;
				return super.isCommandAvailable(command);
			}
		};
		mediaPlayer.addListener(this);
		mediaPlayer.setVolume(1.0f);

		final SessionCommand bookmarkCommand = new SessionCommand("Bookmark", Bundle.EMPTY);
		final CommandButton bookmarkButton = new CommandButton.Builder().setDisplayName("Bookmark").setSessionCommand(bookmarkCommand).setIconResId(R.drawable.outline_bookmark_border_24).build();
		final ImmutableList<CommandButton> customLayout = ImmutableList.of(bookmarkButton);

		// https://github.com/androidx/media/issues/38
		mediaSession = new MediaSession.Builder(this, mediaPlayer)
				.setCallback(new MediaSession.Callback()
				{
					@NonNull
					@Override
					public ListenableFuture<List<MediaItem>> onAddMediaItems(
							@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller,
							@NonNull List<MediaItem> mediaItems)
					{
						//https://stackoverflow.com/questions/74035158/android-media3-session-controller-playback-not-starting
						//https://github.com/androidx/media/issues/156#issuecomment-1222148954
						final String url = mediaItems.get(0).mediaId;

						/* Option 1: To increase timeout and/or other configuration
						final TransferListener transferListener = new TransferListener()
						{
							@Override
							public void onTransferInitializing(@NonNull DataSource dataSource, DataSpec dataSpec, boolean isNetwork)
							{
								Log.v(TAG, "OK initializing transfer for " + dataSpec.getHttpMethodString() + " " + dataSpec.uri + ", " + dataSpec.httpRequestHeaders);
							}

							@Override
							public void onTransferStart(@NonNull DataSource dataSource, @NonNull DataSpec dataSpec, boolean b)
							{
							}

							@Override
							public void onBytesTransferred(@NonNull DataSource dataSource, @NonNull DataSpec dataSpec, boolean b, int i)
							{
							}

							@Override
							public void onTransferEnd(@NonNull DataSource dataSource, @NonNull DataSpec dataSpec, boolean b)
							{
							}
						};

						// Produces DataSource instances through which media data is loaded
						final DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
								.setTransferListener(transferListener)
								.setUserAgent(Util.getUserAgent(getApplicationContext(), getString(R.string.app_name)))
								.setReadTimeoutMs(DEFAULT_READ_TIMEOUT_MILLIS)
								.setAllowCrossProtocolRedirects(true)
								.setConnectTimeoutMs(DEFAULT_CONNECT_TIMEOUT_MILLIS); // Both timeout is 8000

						// Not needed. Only useful for none seekable mp3 files
						// https://exoplayer.dev/progressive.html#enabling-constant-bitrate-seeking
						final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

						// This is the MediaSource representing the media to be played. MediaSource can not be reused
						final ProgressiveMediaSource audioSource1 = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory) // okHttpDataSourceFactory
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
								.createMediaSource(androidx.media3.common.MediaItem.fromUri(Uri.parse(url)));
							*/

						/* Option 2: BlueHost applicable
						// Remove IcyHeaders since streaming is stopped after 5-6 times
						// https://github.com/google/ExoPlayer/issues/7450
						// This is needed for bluehost, dreamhost seems ok so far
						final ProgressiveMediaSource audioSource2 = new ProgressiveMediaSource.Factory(() ->
						{
							if (url.startsWith("http"))
							{
								final HttpDataSource dataSource = new DefaultHttpDataSource(Util.getUserAgent(getApplicationContext(), getString(R.string.app_name)))
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
						}).createMediaSource(androidx.media3.common.MediaItem.fromUri(Uri.parse(url)));
						*/

						// Option 3: Standard. Option 1 & 2 are working as well
						final DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(getApplicationContext());
						final MediaSource.Factory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
						final MediaSource audioSource3 = mediaSourceFactory.createMediaSource(androidx.media3.common.MediaItem.fromUri(Uri.parse(url)));

						final List<MediaItem> mediaList = new ArrayList<>();
						mediaList.add(audioSource3.getMediaItem().buildUpon().setMediaMetadata(mediaItems.get(0).mediaMetadata).build());
						return Futures.immediateFuture(mediaList);
					}

					@NonNull
					@Override
					public MediaSession.ConnectionResult onConnect(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller)
					{
						final MediaSession.ConnectionResult connectionResult = MediaSession.Callback.super.onConnect(session, controller);
						final SessionCommands.Builder availableSessionCommands = connectionResult.availableSessionCommands.buildUpon();
						availableSessionCommands.add(bookmarkCommand);

						return MediaSession.ConnectionResult.accept(
								availableSessionCommands.build(),
								connectionResult.availablePlayerCommands);
					}

					@Override
					public void onPostConnect(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller)
					{
						if (controller.getControllerVersion() != 0)
							session.setCustomLayout(controller, customLayout);
					}

					@NonNull
					@Override
					public ListenableFuture<SessionResult> onCustomCommand
							(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller,
							 @NonNull SessionCommand customCommand, @NonNull Bundle args)
					{
						if (customCommand.customAction.equals("Bookmark"))
						{
							// Send notification to MainActivity (if alive) to shoot saveFavorite() function
							LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(Broadcast_saveFavorite));
						}

						return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
					}
				})
				.build();
		mediaSession.setCustomLayout(customLayout);
	}

	@Nullable
	@Override
	public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo)
	{
		return mediaSession;
	}

	/*
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
	*/

	@Override
	public void onPlayerError(PlaybackException error)
	{
		final Throwable cause = error.getCause();
		if (cause instanceof HttpDataSourceException)
		{
			Toast.makeText(this, "حدث خطأ أثناء تحميل المادة، تأكد من اتصالك بالانترنت وأعد المحاولة", Toast.LENGTH_LONG).show();

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
		{
			if (cause instanceof FileDataSource.FileDataSourceException) // EACCES (Permission denied)
				Toast.makeText(this, "حدث خطأ أثناء تشغيل المادة، تأكد من سماحك للبرنامج للنفاذ إلى الملفات. قم بالضغط على مسار الصوتيات", Toast.LENGTH_LONG).show();
			else
				Toast.makeText(this, "حدث خطأ أثناء تشغيل المادة", Toast.LENGTH_LONG).show();
			Log.e(TAG, "Error: " + cause);
		}
	}

	@Override
	public void onPlaybackStateChanged(int playbackState)
	{
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

	@Override
	public void onDestroy()
	{
		Log.v(TAG, "onDestroy");

		if (mediaPlayer != null)
		{
			mediaPlayer.release();
			mediaPlayer = null;
		}

		if (mediaSession != null)
		{
			mediaSession.release();
			mediaSession = null;
		}

		super.onDestroy();
	}
}