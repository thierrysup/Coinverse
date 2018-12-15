package app.coinverse.content

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import app.coinverse.BuildConfig.DEBUG
import app.coinverse.Enums.UserActionType.*
import app.coinverse.R
import app.coinverse.content.models.Content
import app.coinverse.content.room.CoinverseDatabase
import app.coinverse.databinding.FragmentYoutubeDialogBinding
import app.coinverse.utils.*
import app.coinverse.utils.auth.APP_API_ID_PRODUCTION
import app.coinverse.utils.auth.APP_API_ID_STAGING
import com.google.android.youtube.player.YouTubeInitializationResult
import com.google.android.youtube.player.YouTubePlayer
import com.google.android.youtube.player.YouTubePlayer.OnInitializedListener
import com.google.android.youtube.player.YouTubePlayer.Provider
import com.google.android.youtube.player.YouTubePlayerSupportFragment
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Param
import com.google.firebase.analytics.FirebaseAnalytics.getInstance
import com.google.firebase.auth.FirebaseAuth


class YouTubeDialogFragment : DialogFragment() {

    private var LOG_TAG = YouTubeDialogFragment::class.java.simpleName

    private lateinit var analytics: FirebaseAnalytics
    private lateinit var content: Content
    private lateinit var binding: FragmentYoutubeDialogBinding
    private lateinit var contentViewModel: ContentViewModel
    private lateinit var coinverseDatabase: CoinverseDatabase
    private lateinit var youtubePlayer: YouTubePlayer

    private var seekToPositionMillis = 0

    fun newInstance(bundle: Bundle) = YouTubeDialogFragment().apply { arguments = bundle }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(YOUTUBE_IS_PLAYING_KEY, youtubePlayer.isPlaying)
        outState.putInt(YOUTUBE_CURRENT_TIME_KEY, youtubePlayer.currentTimeMillis)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = getInstance(FirebaseApp.getInstance()!!.applicationContext)
        content = arguments!!.getParcelable(CONTENT_KEY)!!
        contentViewModel = ViewModelProviders.of(this).get(ContentViewModel::class.java)
        coinverseDatabase = CoinverseDatabase.getAppDatabase(context!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        analytics.setCurrentScreen(activity!!, YOUTUBE_VIEW, null)
        binding = FragmentYoutubeDialogBinding.inflate(inflater, container, false)
        val youTubePlayerFragment = YouTubePlayerSupportFragment.newInstance()
        val appApiId: String
        if (DEBUG) { appApiId = APP_API_ID_STAGING } else { appApiId = APP_API_ID_PRODUCTION }
        youTubePlayerFragment.initialize(appApiId, object : OnInitializedListener {
            override fun onInitializationSuccess(provider: Provider, player: YouTubePlayer, wasRestored: Boolean) {
                if (!wasRestored) {
                    youtubePlayer = player
                    player.setPlayerStateChangeListener(PlayerStateChangeListener(savedInstanceState))
                    player.setPlaybackEventListener(PlaybackEventListener())
                    player.loadVideo(content.id.substring(8))
                }
            }

            override fun onInitializationFailure(provider: Provider, result: YouTubeInitializationResult) {
                Log.v(LOG_TAG, String.format("YouTube intialization failed: %s", result.name))
            }
        })
        childFragmentManager.beginTransaction().replace(R.id.youtubePlayer, youTubePlayerFragment as Fragment).commit()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        var width = getDisplayWidth(context!!)
        var height = getDisplayHeight(context!!)
        when (resources.configuration.orientation) {
            ORIENTATION_PORTRAIT -> {
                width = getDisplayWidth(context!!)
                height = (getDisplayHeight(context!!) / YOUTUBE_PORTRAIT_HEIGHT_DIVISOR)
            }
            ORIENTATION_LANDSCAPE -> {
                width = (getDisplayWidth(context!!) / YOUTUBE_LANDSCAPE_WIDTH_DIVISOR).toInt()
                height = (getDisplayHeight(context!!) / YOUTUBE_LANDSCAPE_HEIGHT_DIVISOR).toInt()
            }
        }
        dialog.window!!.setLayout(width, height)
    }

    private inner class PlayerStateChangeListener(var savedInstanceState: Bundle?) : YouTubePlayer.PlayerStateChangeListener {

        override fun onLoading() {}

        override fun onLoaded(videoId: String) {
            if (savedInstanceState != null) {
                youtubePlayer.seekToMillis(savedInstanceState!!.getInt(YOUTUBE_CURRENT_TIME_KEY))
                if (savedInstanceState!!.getBoolean(YOUTUBE_IS_PLAYING_KEY)) {
                    youtubePlayer.play()
                } else {
                    youtubePlayer.pause()
                }
            } else {
                youtubePlayer.play()
            }
        }

        override fun onAdStarted() {}

        override fun onVideoStarted() {
            val bundle = Bundle()
            val user = FirebaseAuth.getInstance().currentUser
            bundle.putString(Param.ITEM_NAME, content.title)
            if (user != null) {
                contentViewModel.updateActions(START, content, user)
                bundle.putString(USER_ID_PARAM, user.uid)
            }
            bundle.putString(CREATOR_PARAM, content.creator)
            analytics.logEvent(START_CONTENT_EVENT, bundle)
        }

        override fun onVideoEnded() {}

        override fun onError(reason: YouTubePlayer.ErrorReason) {}
    }

    private inner class PlaybackEventListener : YouTubePlayer.PlaybackEventListener {
        override fun onPlaying() {}

        override fun onBuffering(isBuffering: Boolean) {}

        override fun onStopped() {}

        override fun onPaused() {}

        override fun onSeekTo(newSeekPositionMillis: Int) {
            if (newSeekPositionMillis > seekToPositionMillis) {
                seekToPositionMillis = newSeekPositionMillis
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Thread(Runnable { run { coinverseDatabase.contentDao().updateContent(content) } }).start()

        val watchPercent =
                ((youtubePlayer.currentTimeMillis.toDouble() - seekToPositionMillis)
                        / youtubePlayer.durationMillis)
        if (watchPercent >= FINISH_THRESHOLD) {
            val bundle = Bundle()
            val user = FirebaseAuth.getInstance().currentUser
            bundle.putString(Param.ITEM_NAME, content.title)
            if (user != null) {
                contentViewModel.updateActions(FINISH, content, user)
                bundle.putString(USER_ID_PARAM, user.uid)
            }
            bundle.putString(CREATOR_PARAM, content.creator)
            analytics.logEvent(FINISH_CONTENT_EVENT, bundle)
        } else if (watchPercent >= CONSUME_THRESHOLD) {
            val bundle = Bundle()
            val user = FirebaseAuth.getInstance().currentUser
            bundle.putString(Param.ITEM_NAME, content.title)
            if (user != null) {
                contentViewModel.updateActions(CONSUME, content, user)
                bundle.putString(USER_ID_PARAM, user.uid)
            }
            bundle.putString(CREATOR_PARAM, content.creator)
            analytics.logEvent(CONSUME_CONTENT_EVENT, bundle)
        }
    }
}

