package nl.fampennings.stimer;

import android.os.Handler;

/**
 * A class that implements a simple timer.
 * The timer does not create a separate thread, so there are no multi-threading issues in your activity.
 * The timer uses an event listener as callback when it expires ("alarm") .
 * 
 * @author Maarten Pennings 2011 September 26
 *
 */
public class STimer {
    
    /** 
     * The minimal value for the period of a timer (in milliseconds).
     */
    public static final int MinPeriod = 10; 
    
    /**
     *  Interface definition for a callback to be invoked when a timer expires ("alarm").
     */
    public interface OnAlarmListener {
        /**
         * Callback that timer 'source' has expired ("alarm").
         * @param source  Timer that expired (when multiple timers use same listener)
         */
        void OnAlarm( STimer source );
    }
    
    protected int             mPeriod= 100;              // what is the period of the timer (in milliseconds)
    protected boolean         mEnabled= false;           // is the timer enabled?
    protected OnAlarmListener mOnAlarmListener= null;    // the listener (callback) for the alarm events
    protected Handler         mHandler= new Handler();   // handler to the thread's queue, allows us to send (timed) messages to that queue 
    protected Runnable        mMessage= new Runnable() { // The message being posted on the message queue
        @Override public void run() 
        { 
            // Call the listener (when it is set).
            if( mOnAlarmListener!=null ) mOnAlarmListener.OnAlarm(STimer.this);
            // Cascade, i.e. post a new delayed message to ourselves.
            // NOTE: The listener could have called stop(), this removes the mMessage message form the queue.
            // However, there is no such message, it has just been taken out of the queue and is currently being executed.
            // So check if the timer is still enabled before cascading (posting a new delayed message).  
            if( mEnabled ) mHandler.postDelayed(mMessage, mPeriod); 
        } 
    }; 
      
    /**
     * Set the period of the timer. The timer must be separately enabled before its starts generating alarm events.
     *  
     * @param ms    the period of the timer in milliseconds
     */
    public void setPeriod( int ms ) {
        if( ms<MinPeriod ) throw new IllegalArgumentException("STimer.setPeriod called with too small period ("+ms+"<"+MinPeriod+")" );
        mPeriod= ms;
    }

    /**
     * Returns the current period of the timer.
     * 
     * @return the current period of the timer in milliseconds
     */
    public int getPeriod( ) {
        return mPeriod;
    }
    
    /**
     * Enables or disables the timer for generating an alarm event every getPeriod milliseconds.
     * 
     * @param enabled   the new state of the timer
     */
    public void setEnabled( boolean enabled ) {
        if( enabled!=mEnabled ) {
            // The enabled state really changes
            if( enabled ) {
                // Post the first message (which will post the next message and so on)
                mHandler.postDelayed(mMessage, mPeriod);  
            } else {
                // Remove any message that is in the queue
                mHandler.removeCallbacks(mMessage);
            }
            // Record the new state
            mEnabled= enabled;
        }
    }

    /**
     * Returns the current enabled/disabled state of the timer.
     * 
     * @return the current enabled/disabled state of the timer
     */
    public boolean getEnabled() {
        return mEnabled;
    }
    
    /**
     * Register a callback to be invoked each time the timer period expires ("alarm").
     *
     * @param l   the listener object that will be called-back.
     */
    public void setOnAlarmListener( OnAlarmListener l ) {
        mOnAlarmListener= l;
    }
    
    /**
     * Starts the timer, i.e. a shorthand for setEnabled(true).
     */
    public void start( ) {
      setEnabled(true);
    }

    /**
     * Stops the timer, i.e. a shorthand for setEnabled(false).
     */
    public void stop( ) {
      setEnabled(false);
    }
    
}
