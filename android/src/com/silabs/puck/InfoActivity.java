/*
 * Activity for selecting which information to display
 *
 * Copyright 2015 Silicon Labs
 *
 * www.silabs.com
 *
 * Author: Quentin Stephenson
 *
 */

package com.silabs.puck;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

public class InfoActivity extends Activity implements OnTouchListener 
{
   private ImageView PuckImage;
   private ImageView PuckOverlay;

   @Override
   protected void onCreate( Bundle savedInstanceState ) 
   {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_info );
      
      getActionBar().setDisplayHomeAsUpEnabled( true );

      /* Find the images */
      PuckImage   = (ImageView)findViewById( R.id.PuckImage   );
      PuckOverlay = (ImageView)findViewById( R.id.PuckOverlay );

      /* Enable touch events */
      PuckImage.setOnTouchListener( this );
   }   
   
   /* This is called when the user touches the puck image */
   public boolean onTouch( View v, MotionEvent Event )
   {
      if ( Event.getAction() == MotionEvent.ACTION_UP )
      {
         /* Create a bitmap of the overlay image */
         PuckOverlay.setDrawingCacheEnabled( true );
         Bitmap OverlayBitmap = Bitmap.createBitmap( PuckOverlay.getDrawingCache() );
         PuckOverlay.setDrawingCacheEnabled( false );

         /* Get the color of the pixel in the overlay image where the touch occured */
         int OverlayColor = OverlayBitmap.getPixel( (int)Event.getX(), (int)Event.getY() );

         /* Call the appropriate click handler based on the pixel color */
         if ( CloseMatch(OverlayColor,Color.YELLOW) )
            OnTemperatureClick( v );
         else if ( CloseMatch(OverlayColor,Color.RED) )
            OnOpticalClick( v );
         else if ( CloseMatch(OverlayColor,Color.GREEN) )
            OnBoostClick( v );
         else if ( CloseMatch(OverlayColor,Color.CYAN) )
            OnMCUClick( v );
         else if ( CloseMatch(OverlayColor,Color.BLUE) )
            OnPuckClick( v );
      }      
   
      return true;
   }
   
   public boolean CloseMatch( int Color1, int Color2 )
   {
      if ( (int)Math.abs(Color.red(Color1)-Color.red(Color2)) > 25 )
         return false;
      if ( (int)Math.abs(Color.green(Color1)-Color.green(Color2)) > 25 )
         return false;
      if ( (int)Math.abs(Color.blue(Color1)-Color.blue(Color2)) > 25 )
         return false;
      return true;   
   }
   
   public void OnTemperatureClick( final View v )
   {
      Intent intent = new Intent( this, WebActivity.class );
      intent.putExtra( "URL", 
         "http://www.silabs.com/products/sensors/humidity-sensors/Pages/si7013-20-21.aspx" );
      startActivity( intent );
   }

   public void OnOpticalClick( final View v )
   {
      Intent intent = new Intent( this, WebActivity.class );
      intent.putExtra( "URL", 
         "http://www.silabs.com/products/sensors/infraredsensors/Pages/Si114x.aspx" );
      startActivity( intent );
   }

   public void OnBoostClick( final View v )
   {
      Intent intent = new Intent( this, WebActivity.class );
      intent.putExtra( "URL", 
        "http://www.silabs.com/products/analog/dc-dc-converter/Pages/default.aspx" );
      startActivity( intent );
   }

   public void OnMCUClick( final View v )
   {
      Intent intent = new Intent( this, WebActivity.class );
      intent.putExtra( "URL", 
         "http://www.silabs.com/products/mcu/32-bit/efm32-gecko/pages/efm32-gecko.aspx" );
      startActivity( intent );
   }

   public void OnPuckClick( final View v )
   {
      Intent intent = new Intent( this, WebActivity.class );
      intent.putExtra( "URL", 
         "http://www.silabs.com/products/sensors/Pages/environmental-biometric-sensor-puck.aspx" );
      startActivity( intent );
   }

}
