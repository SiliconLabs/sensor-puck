/*
 * Activity to display information in a web page
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
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class WebActivity extends Activity 
{
   @Override
   protected void onCreate( Bundle savedInstanceState ) 
   {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_web );
      
      getActionBar().setDisplayHomeAsUpEnabled( true );
      
      /* Get the URL from the intent */
      Intent intent = getIntent();
      String URL = intent.getStringExtra( "URL" );

      /* Find the web view */
      WebView WebPage = (WebView)findViewById( R.id.WebPage );

      /* Enable JavaScript */
      WebSettings Settings = WebPage.getSettings();
      Settings.setJavaScriptEnabled( true );

      /* Display the web page */
      WebPage.loadUrl( URL );
   }   
}

