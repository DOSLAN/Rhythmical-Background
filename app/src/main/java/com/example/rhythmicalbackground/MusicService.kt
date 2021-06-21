package com.example.rhythmicalbackground

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.chameleody.activity.PlayerActivity
import com.example.chameleody.files_manager.FilesManager
import kotlin.random.Random

class MusicService : Service(){
    companion object {
        const val REPEAT_ONE = 1
        const val REPEAT_ALL = 2
        const val SHUFFLE_ALL = 3
        const val SHUFFLE_SMART = 4
        const val MSG_REGISTER_CLIENT = 0
        const val MSG_COMPLETED = 1
    }
    private val fm = FilesManager.instance
    private var mBinder: IBinder = MyBinder()
    lateinit var mediaPlayer: MediaPlayer
    private lateinit var mediaSessionCompat: MediaSessionCompat
    val duration get() = mediaPlayer.duration
    val currentPosition get() = mediaPlayer.currentPosition
    val isPlaying get() = mediaPlayer.isPlaying
    private lateinit var uri : Uri

    override fun onCreate() {
        super.onCreate()
        mediaSessionCompat = MediaSessionCompat(baseContext, "My Audio")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.e("Bind", "Method")
        return mBinder
    }

    inner class MyBinder : Binder() {
        val service: MusicService get() = this@MusicService

    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val myPosition = intent.getIntExtra("servicePosition", -1)
        val actionName = intent.getStringExtra("ActionName")
        if (myPosition != -1) {
            playMedia(myPosition)
        }
        if (actionName!=null){
            when(actionName){
                "playPause" -> {
                    Toast.makeText(this, "PlayPause", Toast.LENGTH_LONG).show()
                    playPauseBtnClicked()
                }
                "next" -> {
                    Toast.makeText(this,"Next", Toast.LENGTH_SHORT).show()
                    nextBtnClicked()
                }
                "previous" -> {
                    Toast.makeText(this,"Previous", Toast.LENGTH_SHORT).show()
                    prevBtnClicked()
                }
            }
        }
        return START_STICKY
    }

    fun playMedia(startPosition : Int) {
        fm.currentSongPos = startPosition
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        createMediaPlayer(fm.currentSongPos)
        mediaPlayer.start()
    }

    private fun createMediaPlayer(positionInner : Int) {
        if (positionInner < fm.currentSongs.size) {
            fm.currentSongPos = positionInner
            uri = Uri.parse(fm.currentSong.path)
            mediaPlayer = MediaPlayer.create(baseContext, uri)
        }
    }

    fun playPauseBtnClicked(){
        if (isPlaying) mediaPlayer.pause()
        else mediaPlayer.start()
    }

    fun nextBtnClicked(){
        mediaPlayer.stop()
        mediaPlayer.release()
        when(fm.currentShuffle) {
            REPEAT_ALL, REPEAT_ONE -> fm.currentSongPos = ((fm.currentSongPos + 1) % fm.currentSongs.size)
            SHUFFLE_ALL -> fm.currentSongPos = Random.nextInt(0,fm.currentSongs.size)
            SHUFFLE_SMART -> TODO()
        }
        createMediaPlayer(fm.currentSongPos)
        initListener()
        mediaPlayer.start()
    }

    fun prevBtnClicked(){
        mediaPlayer.stop()
        mediaPlayer.release()
        when(fm.currentShuffle) {
            REPEAT_ALL, REPEAT_ONE -> fm.currentSongPos = if(fm.currentSongPos==0) fm.currentSongs.size-1
            else fm.currentSongPos -1
            SHUFFLE_ALL -> fm.currentSongPos = Random.nextInt(0,fm.currentSongs.size)
            SHUFFLE_SMART -> TODO()
        }
        createMediaPlayer(fm.currentSongPos)
        initListener()
        mediaPlayer.start()
    }

    fun seekTo(position : Int){
        mediaPlayer.seekTo(position)
    }

    fun initListener() {
        mediaPlayer.setOnCompletionListener{
            mediaPlayer.stop()
            mediaPlayer.release()
            when(fm.currentShuffle) {
                REPEAT_ALL -> fm.currentSongPos = ((fm.currentSongPos + 1) % fm.currentSongs.size)
                SHUFFLE_ALL -> fm.currentSongPos = Random.nextInt(0,fm.currentSongs.size)
                SHUFFLE_SMART -> TODO()
            }
            createMediaPlayer(fm.currentSongPos)
            initListener()
            mediaPlayer.start()
            sentMsg(MSG_COMPLETED)
        }
    }

    fun showNotification(playPauseBtn : Int){
        //val intent = Intent(this, PlayerActivity::class.java)
        //val contentIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
        val prevIntent = Intent(this, PlayerActivity::class.java).setAction(ApplicationClass.ACTION_PREVIOUS)
        val prevPending = PendingIntent.getBroadcast(this, 0, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val pauseIntent = Intent(this, PlayerActivity::class.java).setAction(ApplicationClass.ACTION_PLAY)
        val pausePending = PendingIntent.getBroadcast(this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val nextIntent = Intent(this, PlayerActivity::class.java).setAction(ApplicationClass.ACTION_NEXT)
        val nextPending = PendingIntent.getBroadcast(this, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val picture = getAlbumArt(fm.currentSong.path)
        val thumb = if (picture!=null){
            BitmapFactory.decodeByteArray(picture, 0, picture.size) }
        else{
            BitmapFactory.decodeResource(resources, R.drawable.default_art)
        }
        val notification = NotificationCompat.Builder(this, ApplicationClass.CHANNEL_ID_2).
        setSmallIcon(playPauseBtn).setLargeIcon(thumb).
        setContentTitle(fm.currentSong.name).setContentText(fm.currentSong.artist).
        addAction(R.drawable.prev, "Previous", prevPending).
        addAction(playPauseBtn, "Pause", pausePending).
        addAction(R.drawable.next, "Next", nextPending).
        setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSessionCompat.sessionToken)).
        setPriority(NotificationCompat.PRIORITY_HIGH).
        setOnlyAlertOnce(true).
        build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, notification)
    }

    private fun getAlbumArt(uri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(uri)
        val art = retriever.embeddedPicture
        retriever.release()
        return art
    }

    private fun sentMsg(msg: Int){
        for(client in clients){
            try{
                client.send(Message.obtain(null, msg))
            }catch (e: RemoteException){
                clients.remove(client)
            }
        }
    }

    val messenger = Messenger(IncomingHandler())
    val clients = ArrayList<Messenger>()
    inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            if(msg.what == MSG_REGISTER_CLIENT){
                clients.add(msg.replyTo)
            }else super.handleMessage(msg)
        }
    }
}