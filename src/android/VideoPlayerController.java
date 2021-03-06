package com.bais.cordova.video;

import cn.com.ebais.kyytvhismart.*;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.RegionIterator;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.Locale;

public class VideoPlayerController extends FrameLayout {
	
private static final String TAG = "VideoControllerView";

private MediaPlayerControl mPlayer;
private Context            mContext;
private ViewGroup          mAnchor;
private View               mRoot;
private ProgressBar        mProgress;
private TextView           mEndTime, mCurrentTime;
private boolean            mShowing;
private boolean            mDragging;
private static final int     sDefaultTimeout = 5000;
private static final int     FADE_OUT = 1;
private static final int     SHOW_PROGRESS = 2;
private static final int     SHOW_BUFFER = 0;
private int 				  buffernull = 0;
private int 				  bufferwidth = 0;
private boolean           mUseFastForward;
private boolean           mFromXml;
private boolean           mListenersSet;
private OnClickListener 	  mNextListener, mPrevListener;
StringBuilder             mFormatBuilder;
Formatter                 mFormatter;
private ImageButton       mPauseButton;
private ImageButton       mFfwdButton;
private ImageButton       mRewButton;
private ImageButton       mNextButton;
private ImageButton       mPrevButton;
private Handler           mHandler = new MessageHandler(this);

public VideoPlayerController(Context context, AttributeSet attrs) {
    super(context, attrs);
    mRoot = null;
    mContext = context;
    mUseFastForward = true;
    mFromXml = true;

    Log.i(TAG, TAG);
}

public VideoPlayerController(Context context, boolean useFastForward) {
    super(context);
    mContext = context;
    mUseFastForward = useFastForward;

    Log.i(TAG, TAG);
}

public VideoPlayerController(Context context) {
    this(context, true);

    Log.i(TAG, TAG);
}

@Override
public void onFinishInflate() {
    if (mRoot != null)
        initControllerView(mRoot);
}

public void setMediaPlayer(MediaPlayerControl player) {
    mPlayer = player;
    updatePausePlay();
    //updateFullScreen();
}

public void setAnchorView(ViewGroup view) {
    mAnchor = view;

    LayoutParams frameParams = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
    );

    removeAllViews();
    View v = makeControllerView();
    addView(v, frameParams);
}

protected View makeControllerView() {
    LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mRoot = inflate.inflate(R.layout.video_controller, null);
    initControllerView(mRoot);
    return mRoot;
}

public void initControllerView(View v) {
    mPauseButton = (ImageButton) v.findViewById(R.id.pause);
    if (mPauseButton != null) {
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(mPauseListener);
    }
    mFfwdButton = (ImageButton) v.findViewById(R.id.ffwd);
    if (mFfwdButton != null) {
        mFfwdButton.setOnClickListener(mFfwdListener);
        if (!mFromXml) {
            mFfwdButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
        }
    }

    mRewButton = (ImageButton) v.findViewById(R.id.rew);
    if (mRewButton != null) {
        mRewButton.setOnClickListener(mRewListener);
        if (!mFromXml) {
            mRewButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
        }
    }

    // By default these are hidden. They will be enabled when setPrevNextListeners() is called 
    mNextButton = (ImageButton) v.findViewById(R.id.next);
    if (mNextButton != null && !mFromXml && !mListenersSet) {
        mNextButton.setVisibility(View.GONE);
    }
    mPrevButton = (ImageButton) v.findViewById(R.id.prev);
    if (mPrevButton != null && !mFromXml && !mListenersSet) {
        mPrevButton.setVisibility(View.GONE);
    }

    mProgress = (ProgressBar) v.findViewById(R.id.mediacontroller_seekbar);
    if (mProgress != null) {
        if (mProgress instanceof SeekBar) {
        	SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
            bufferwidth = seeker.getProgress();
        }
        mProgress.setMax(1000);
    }
    
    /*mProgress.getViewTreeObserver().addOnPreDrawListener(
    	new ViewTreeObserver.OnPreDrawListener() {
	    	 public boolean onPreDraw() {
	    		 bufferwidth = mProgress.getMeasuredWidth();
	    		 return true;
	    	 }
    	});*/    
    
    mEndTime = (TextView) v.findViewById(R.id.time);
    mCurrentTime = (TextView) v.findViewById(R.id.time_current);
    mFormatBuilder = new StringBuilder();
    mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

    installPrevNextListeners();
    //show(sDefaultTimeout);
}



public void show() {
    show(sDefaultTimeout);
}


private void disableUnsupportedButtons() {
    if (mPlayer == null) {
        return;
    }

    try {
        if (mPauseButton != null && !mPlayer.canPause()) {
            mPauseButton.setEnabled(false);
        }
        if (mRewButton != null && !mPlayer.canSeekBackward()) {
            mRewButton.setEnabled(false);
        }
        if (mFfwdButton != null && !mPlayer.canSeekForward()) {
            mFfwdButton.setEnabled(false);
        }
    } catch (IncompatibleClassChangeError ex) {}
}

public void show(int timeout) {
    if (!mShowing && mAnchor != null) {
        setProgress();
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
        }
        disableUnsupportedButtons();

        LayoutParams tlp = new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        );

        mAnchor.addView(this, tlp);
        mShowing = true;
    }
    updatePausePlay();

    mHandler.sendEmptyMessage(SHOW_PROGRESS);
    Message msg = mHandler.obtainMessage(FADE_OUT);
    if (timeout != 0) {
        mHandler.removeMessages(FADE_OUT);
        mHandler.sendMessageDelayed(msg, timeout);
    }
}

public boolean isShowing() {
    return mShowing;
}

public void hide() {
    if (mAnchor == null) {
        return;
    }

    try {
        mAnchor.removeView(this);
        mHandler.removeMessages(SHOW_PROGRESS);
    } catch (IllegalArgumentException ex) {
        Log.w("MediaController", "already removed");
    }
    mShowing = false;
}

private String stringForTime(int timeMs) {
    int totalSeconds = timeMs / 1000;

    int seconds = totalSeconds % 60;
    int minutes = (totalSeconds / 60) % 60;
    int hours   = totalSeconds / 3600;

    mFormatBuilder.setLength(0);
    if (hours > 0) {
        return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
    } else {
        return mFormatter.format("%02d:%02d", minutes, seconds).toString();
    }
}

private int setProgress() {
    if (mPlayer == null || mDragging) {
        return 0;
    }

    int position = mPlayer.getCurrentPosition();
    int duration = mPlayer.getDuration();
    if (mProgress != null) {
        if (duration > 0) {
            // use long to avoid overflow
            long pos = 1000L * position / duration;
            mProgress.setProgress( (int) pos);
        }
        /*int percent = mPlayer.getBufferPercentage();
        //Toast.makeText(getContext(), percent, Toast.LENGTH_SHORT).show();
        mProgress.setSecondaryProgress(percent * 10);*/
    }

    if (mEndTime != null)
        mEndTime.setText(stringForTime(duration));
    if (mCurrentTime != null)
        mCurrentTime.setText(stringForTime(position));

    return position;
}


public void onBufferingUpdate(int percent) {
	//Toast.makeText(getContext(), ">>", Toast.LENGTH_SHORT).show();
	//int currentProgress = mProgress.getMax() * mPlayer.getCurrentPosition() / mPlayer.getDuration(); 
	mHandler.sendEmptyMessage(SHOW_BUFFER);
	buffernull = percent;
}

@Override
public boolean onTouchEvent(MotionEvent event) {
    //show(sDefaultTimeout);
    return true;
}

@Override
public boolean onTrackballEvent(MotionEvent ev) {
    //show(sDefaultTimeout);
    return false;
}

@Override
public boolean dispatchKeyEvent(KeyEvent event) {
    if (mPlayer == null) {
        return true;
    }
    return super.dispatchKeyEvent(event);
}

public void dispatchright(){
	int pos = mPlayer.getCurrentPosition();
    pos += 50000; // milliseconds
    setProgress();
    mPlayer.seekTo(pos);
}
public void dispatchleft(){
	int pos = mPlayer.getCurrentPosition();
    pos -= 50000; // milliseconds
    setProgress();
    mPlayer.seekTo(pos);
}

private OnClickListener mPauseListener = new OnClickListener() {
    public void onClick(View v) {
        doPauseResume();
        show(sDefaultTimeout);
    }
};

public void updatePausePlay() {
    if (mRoot == null || mPauseButton == null || mPlayer == null) {
        return;
    }

    if (mPlayer.isPlaying()) {
        // mPauseButton.setImageResource(R.drawable.pause);
         mPauseButton.setBackgroundResource(R.drawable.pause);
    } else {
        // mPauseButton.setImageResource(R.drawable.play);
         mPauseButton.setBackgroundResource(R.drawable.play);
    }
}

private void doPauseResume() {
    if (mPlayer == null) {
        return;
    }

    if (mPlayer.isPlaying()) {
        mPlayer.pause();
    } else {
        mPlayer.start();
    }
    updatePausePlay();
}


private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
    public void onStartTrackingTouch(SeekBar bar) {
        show(3600000);
        mDragging = true;
        mHandler.removeMessages(SHOW_PROGRESS);
    }

    public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
        if (mPlayer == null) {
            return;
        }

        if (!fromuser) {
            return;
        }

        long duration = mPlayer.getDuration();
        long newposition = (duration * progress) / 1000L;
        mPlayer.seekTo( (int) newposition);
        if (mCurrentTime != null)
            mCurrentTime.setText(stringForTime( (int) newposition));
    }

    public void onStopTrackingTouch(SeekBar bar) {
        mDragging = false;
        setProgress();
        updatePausePlay();
        show(sDefaultTimeout);

        mHandler.sendEmptyMessage(SHOW_PROGRESS);
    }
};

@Override
public void setEnabled(boolean enabled) {
    if (mPauseButton != null) {
        mPauseButton.setEnabled(enabled);
    }
    if (mFfwdButton != null) {
        mFfwdButton.setEnabled(enabled);
    }
    if (mRewButton != null) {
        mRewButton.setEnabled(enabled);
    }
    if (mNextButton != null) {
        mNextButton.setEnabled(enabled && mNextListener != null);
    }
    if (mPrevButton != null) {
        mPrevButton.setEnabled(enabled && mPrevListener != null);
    }
    if (mProgress != null) {
        mProgress.setEnabled(enabled);
    }
    disableUnsupportedButtons();
    super.setEnabled(enabled);
}

private OnClickListener mRewListener = new OnClickListener() {
    public void onClick(View v) {
        if (mPlayer == null) {
            return;
        }

        int pos = mPlayer.getCurrentPosition();
        pos -= 20000; // milliseconds
        setProgress();
        mPlayer.seekTo(pos);
        show(sDefaultTimeout);
    }
};

private OnClickListener mFfwdListener = new OnClickListener() {
    public void onClick(View v) {
        if (mPlayer == null) {
            return;
        }

        int pos = mPlayer.getCurrentPosition();
        pos += 20000; // milliseconds
        setProgress();
        mPlayer.seekTo(pos);
        show(sDefaultTimeout);
    }
};

private void installPrevNextListeners() {
    if (mNextButton != null) {
        mNextButton.setOnClickListener(mNextListener);
        mNextButton.setEnabled(mNextListener != null);
    }

    if (mPrevButton != null) {
        mPrevButton.setOnClickListener(mPrevListener);
        mPrevButton.setEnabled(mPrevListener != null);
    }
}

public void setPrevNextListeners(OnClickListener next, OnClickListener prev) {
    mNextListener = next;
    mPrevListener = prev;
    mListenersSet = true;

    if (mRoot != null) {
        installPrevNextListeners();

        if (mNextButton != null && !mFromXml) {
            mNextButton.setVisibility(View.VISIBLE);
        }
        if (mPrevButton != null && !mFromXml) {
            mPrevButton.setVisibility(View.VISIBLE);
        }
    }
}

private float changeSizeScale;
private Region cachRegion;

private void drawCachProgress(Canvas canvas,int progressBarWidth,int pTop,int pBottom){  
    if(changeSizeScale!=0){
        Region region=getCachRegion(pTop,pBottom);  
        cachRegion=new Region(0, pTop,1, pBottom);   
        RegionIterator iter = new RegionIterator(region);    
        Rect r = new Rect();     
        while (iter.next(r)) {    
            r.set((int)(r.left*changeSizeScale), r.top,(int)(r.right*changeSizeScale), r.bottom);  
            cachRegion.op(r, Op.UNION);
        }     
        changeSizeScale=0;  
    } 
}
    
    private Region getCachRegion(int pTop,int pBottom) {  
        if(cachRegion==null){  
            cachRegion=new Region(0, pTop,1, pBottom);  
        }  
        return cachRegion;  
    }  


public interface MediaPlayerControl {
    void    start();
    void    pause();
    int     getDuration();
    int     getCurrentPosition();
    void    seekTo(int pos);
    boolean isPlaying();
    int     getBufferPercentage();
    boolean canPause();
    boolean canSeekBackward();
    boolean canSeekForward();
    boolean isFullScreen();
    void    toggleFullScreen();
}

private static class MessageHandler extends Handler {
    private final WeakReference<VideoPlayerController> mView; 

    MessageHandler(VideoPlayerController view) {
        mView = new WeakReference<VideoPlayerController>(view);
    }
    @Override
    public void handleMessage(Message msg) {
        VideoPlayerController view = mView.get();
        
        if (view == null || view.mPlayer == null) {
            return;
        }

        int pos;
        switch (msg.what) {
            case FADE_OUT:
                view.hide();
                break;
            case SHOW_PROGRESS:
                pos = view.setProgress();
                if (!view.mDragging && view.mShowing && view.mPlayer.isPlaying()){
                    msg = obtainMessage(SHOW_PROGRESS);
                    sendMessageDelayed(msg, 1000 - (pos % 1000));
                }
                break;
            case SHOW_BUFFER:          	
            	double v1 = view.buffernull;
            	double v2 = 0.1;
            	int ii = (int)((v1*v2)*100);
            	view.mProgress.setSecondaryProgress(ii);
	    		// Log.i("width.......", ii+">>>>>>>"); 
        }
    }
   
}
}
