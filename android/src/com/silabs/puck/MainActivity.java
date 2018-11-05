/*
 * Android app for displaying sensor measurements from a Sensor Puck
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
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.app.Fragment;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import com.androidplot.Plot.BorderStyle;
import com.androidplot.xy.*;
import java.util.ArrayList;
import java.util.Arrays;
import nl.fampennings.stimer.STimer;
import nl.fampennings.stimer.STimer.OnAlarmListener;


public class MainActivity extends Activity 
{
   /* Sensor Data types */
   public static final int SD_MODE        = 0;
   public static final int SD_SEQUENCE    = 1;
   public static final int SD_HUMIDITY    = 2;
   public static final int SD_TEMPERATURE = 3;
   public static final int SD_AMB_LIGHT   = 4;
   public static final int SD_UV_LIGHT    = 5;
   public static final int SD_BATTERY     = 6;
   public static final int SD_HRM_STATE   = 16;
   public static final int SD_HRM_RATE    = 17;
   public static final int SD_HRM_SAMPLE  = 18;

   /* Measurement Mode */
   public static final int ENVIRONMENTAL_MODE = 0;
   public static final int BIOMETRIC_MODE     = 1;
   public static final int PENDING_MODE       = 8;
   public static final int NOT_FOUND_MODE     = 9;

   /* Heart Rate Monitor state */
   public static final int HRM_STATE_IDLE      = 0;
   public static final int HRM_STATE_NOSIGNAL  = 1;
   public static final int HRM_STATE_ACQUIRING = 2;
   public static final int HRM_STATE_ACTIVE    = 3;
   public static final int HRM_STATE_INVALID   = 4;
   public static final int HRM_STATE_ERROR     = 5;

   public static final int HRM_SAMPLE_COUNT = 5;
   public static final int MAX_PUCK_COUNT   = 16;
   public static final int MAX_IDLE_COUNT   = 3;

   public static final int GRAPH_RANGE_SIZE  = 40000;
   public static final int GRAPH_DOMAIN_SIZE = 50;

   private BluetoothAdapter Adapter;
   private Handler SensorHandler = new Handler( new onSensorMessage() );
   
   /* Fragments */
   private NotFoundFragment      NotFragment = new NotFoundFragment();
   private EnvironmentalFragment EnvFragment = new EnvironmentalFragment();
   private BiometricFragment     BioFragment = new BiometricFragment();

   private EditText  PuckName;
   private ImageView EditButton;
   private ImageView Spacer;
   
   private InputMethodManager IME_Manager;
   
   private int CurrentMode = NOT_FOUND_MODE;

   private STimer PuckTimer;
   private SharedPreferences FriendlyNames;
   
   private boolean Fahrenheit = false;
   
   /* Advertisement structure */
   private class Advertisement
   {
      String Address;
      byte[] Data;
   }

   /* Puck data structure */
   private class Puck
   {
      /* Identification */
      String Address;
      String Name;

      /* Sensor data */
      int    MeasurementMode;
      int    Sequence;
      float  Humidity;
      float  Temperature;
      int    AmbientLight;
      int    UV_Index;
      float  Battery;
      int    HRM_State;
      int    HRM_Rate;
      int[]  HRM_Sample;
      int    HRM_PrevSample;

      /* Statistics */
      int    PrevSequence;
      int    RecvCount;
      int    PrevCount;
      int    UniqueCount;
      int    LostAdv;
      int    LostCount;
      int    IdleCount;
   }

   /* Puck Management */
   Puck[] PuckArray;
   int    PuckCount     = 0;
   int    PrevPuckCount = 0;
   Puck   SelectedPuck  = null;

   /* Navigation drawer */
   private ArrayList<String>     PuckNames;
   private DrawerLayout          PuckDrawer;
   private ArrayAdapter<String>  PuckAdapter;
   private ListView              PuckList;
   private ActionBarDrawerToggle PuckToggle;

   /* This is called with the app is started */
   @Override
   protected void onCreate( Bundle savedInstanceState ) 
   {
      super.onCreate( savedInstanceState );
      setContentView( R.layout.activity_main );

      /* Init the EditText field which displays the name of the puck */
      PuckName  = (EditText)findViewById( R.id.PuckName );
      PuckName.setOnClickListener( OnPuckNameClick );
      PuckName.setOnEditorActionListener( OnPuckNameAction );

      /* Register a listener for handling edit button clicks */
      EditButton = (ImageView)findViewById( R.id.Edit_Button );
      EditButton.setOnClickListener( OnEditClick );

      /* Get a handler to the spacer image */
      Spacer = (ImageView)findViewById( R.id.Spacer );

      /* Init the navigation drawer */
      PuckDrawer  = (DrawerLayout)findViewById( R.id.drawer_layout );
      PuckList    = (ListView)findViewById( R.id.left_drawer );
      PuckNames   = new ArrayList<String>();
      PuckAdapter = new ArrayAdapter<String>( this, R.layout.drawer_list_item, android.R.id.text1, PuckNames );
      PuckList.setAdapter( PuckAdapter );
      PuckList.setOnItemClickListener( OnPuckItemClick );

      /* Init the app icon to open/close the navigation drawer */
      PuckToggle = new ActionBarDrawerToggle( this, PuckDrawer, R.drawable.ic_drawer, 
         R.string.drawer_open, R.string.drawer_close ) {};
      PuckDrawer.setDrawerListener( PuckToggle );
      getActionBar().setDisplayHomeAsUpEnabled( true );
      getActionBar().setHomeButtonEnabled( true );

      /* Get the Input Method Manager for showing and hiding the keyboard */
      IME_Manager = (InputMethodManager)getSystemService( Context.INPUT_METHOD_SERVICE );

      /* Init Bluetooth */
      BluetoothManager Manager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
      Adapter = Manager.getAdapter();
      if ( Adapter==null || !Adapter.isEnabled() )
      {
         /* Ask the user to turn on Bluetooth */
         Intent EnableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
         startActivityForResult( EnableIntent, 1 );
      }

      /* Init the puck array */
      PuckArray = new Puck[MAX_PUCK_COUNT];
      for ( int x=0; x<MAX_PUCK_COUNT; x++ )
      {
         PuckArray[x] = new Puck();
         PuckArray[x].HRM_Sample = new int[HRM_SAMPLE_COUNT];
      }   

      /* Init the periodic timer */
      PuckTimer = new STimer();
      PuckTimer.setOnAlarmListener( OnPuckTick );
      PuckTimer.setPeriod( 1000 );

      /* Open the persistent storage */
      FriendlyNames = getSharedPreferences("FriendlyNames", 0 );

      /* Display the "not found" fragment */
      SensorHandler.sendEmptyMessage( 0 );
   }

   /* This is called when the app is displayed */
   @Override
   protected void onStart()
   {
      super.onStart();

      /* Start the periodic timer */
      PuckTimer.start();

      /* Start scanning for BLE advertisements */
      if ( Adapter!=null && Adapter.isEnabled() )
         Adapter.startLeScan( ScanCallback );
   }

   /* This called when the app is no longer displayed */
   @Override
   protected void onStop()
   {
      super.onStop();

      /* Stop scanning for BLE advertisements */
      if ( Adapter!=null && Adapter.isEnabled() )
         Adapter.stopLeScan( ScanCallback );

      /* Stop the periodic timer */
      PuckTimer.stop();
   }

   @Override
   protected void onPostCreate( Bundle savedInstanceState ) 
   {
      super.onPostCreate( savedInstanceState );

      /* Sync the toggle state after onRestoreInstanceState has occurred */
      PuckToggle.syncState();
   }

   @Override
   public void onConfigurationChanged( Configuration newConfig ) 
   {
      super.onConfigurationChanged( newConfig );
      PuckToggle.onConfigurationChanged( newConfig );
   }

   /* This is called when the app is started */
   @Override
   public boolean onCreateOptionsMenu( Menu menu ) 
   {
      return true;
   }

   /* This is called when the user selects a puck from the menu */
   @Override
   public boolean onOptionsItemSelected( MenuItem item ) 
   {
      /* If the app icon was tapped then PuckToggle will handle it */
      if ( PuckToggle.onOptionsItemSelected(item) ) 
         return true;
      else   
         return super.onOptionsItemSelected(item);
   }
   
   /* This called when a puck name in the navigation drawer is tapped */
   final OnItemClickListener OnPuckItemClick = new OnItemClickListener()
   {
      public void onItemClick( AdapterView parent, View view, int position, long id ) 
      {
         int x;

         /* Get puck name that was tapped */
         String Name = PuckNames.get( position );
         
         /* Search for the puck name in the puck array */
         for ( x=0; x<PuckCount; x++ )
            if ( Name.equals(PuckArray[x].Name) )
               break;

         /* If the puck was found */
         if ( x < PuckCount )
         {
            /* Select the puck */
            SelectedPuck = PuckArray[x];
            PuckName.setText( SelectedPuck.Name );
         }

         /* Close the drawer */
         PuckDrawer.closeDrawer( PuckList );
      }
   };

   /* This is called when the user taps on the edit button */
   final OnClickListener OnEditClick = new OnClickListener()
   {
      public void onClick( final View v )
      {
         /* Ensure that the PuckName field has the focus */
         PuckName.requestFocus();
      
         /* Make the cursor visible in the PuckName field */
         PuckName.setCursorVisible( true );

         /* Move the cursor to the end of the text */
         PuckName.setSelection( PuckName.getText().length() );

         /* Show the keyboard */
         IME_Manager.showSoftInput( PuckName, 0 );
      }
   };

   /* This is called when the user taps in the PuckName edit field */
   final OnClickListener OnPuckNameClick = new OnClickListener()
   {
      public void onClick( final View v )
      {
         /* Make the cursor visible */
         PuckName.setCursorVisible( true );
         
         /* Android automatically shows the keyboard */
      }
   };
   
   /* This is called when the user taps on the "Done" key in the keyboard */
   final OnEditorActionListener OnPuckNameAction = new OnEditorActionListener()
   {
      public boolean onEditorAction( TextView v, int actionId, KeyEvent event )
      {
         if ( actionId == EditorInfo.IME_ACTION_DONE ) 
         {
            /* Make the cursor invisible */
            PuckName.setCursorVisible( false );

            /* Get the puck name that the user entered */
            String Name = PuckName.getText().toString();

            /* Get the editor for the persistent storage */
            SharedPreferences.Editor Editor = FriendlyNames.edit();

            /* If the user completely erased the puck name */
            if ( Name.length() == 0 )
            {
               /* Remove the puck name from persistent storage */
               Editor.remove( SelectedPuck.Address );
               
               /* Switch to the default puck name */
               Name = DefaultName( SelectedPuck.Address );
               PuckName.setText( Name );
            }
            /* Save the new puck name in persistent storage */
            else Editor.putString( SelectedPuck.Address, Name );

            Editor.commit();

            /* Change the name in the navigation drawer */
            PuckNames.remove( SelectedPuck.Name );
            PuckNames.add( Name );
            PuckAdapter.notifyDataSetChanged();
            PuckList.setItemChecked( PuckCount-1, true );

            /* Save the new name in the puck data structure */
            SelectedPuck.Name = Name;
         }
         
         return false; /* Dismiss the keyboard */
      }
   };

   /* Create the default puck name */
   private String DefaultName( String Address )
   {
      String[] Part = Address.split(":");
      return "Puck_" + Part[4] + Part[5];
   }

   /* Find a puck in the puck array or add the puck to the puck array */
   /* This routine does not run on the UI thread */
   private synchronized Puck FindPuck( String Address )
   {
      /* Search for the puck in the puck array */
      for ( int x=0; x<PuckCount; x++ )
         if ( Address.equals(PuckArray[x].Address) )
            return PuckArray[x];

      /* If the puck array is full then return null */
      if ( PuckCount == MAX_PUCK_COUNT )
         return null;

      /* Add the puck to the puck array */
      PuckArray[PuckCount].Address = Address;
      PuckArray[PuckCount].Name = FriendlyNames.getString( Address, DefaultName(Address) );
      PuckArray[PuckCount].PrevSequence =  0;
      PuckArray[PuckCount].RecvCount    =  0;
      PuckArray[PuckCount].PrevCount    = -1;
      PuckArray[PuckCount].UniqueCount  =  0;
      PuckArray[PuckCount].LostCount    =  0;
      PuckArray[PuckCount].IdleCount    =  0;

      /* If this is the first puck then select it */
      if ( PuckCount == 0 )
         SelectedPuck = PuckArray[PuckCount];

      return PuckArray[PuckCount++];   
   }

   /* Delete a puck from the puck array */
   private synchronized void DeletePuck( int Index )
   {
      /* There is one less puck now */
      PuckCount--;

      /* If the puck array is not empty */
      if ( PuckCount > 0 )
      {
         /* Find the index of the selected puck */
         for ( int x=0; x<=PuckCount; x++ )
         {
            if ( PuckArray[x] == SelectedPuck )
            {
               /* If the selected puck is being deleted */
               if ( x == Index )
               {
                  /* Select another puck */
                  if ( x > 0 )
                     SelectedPuck = PuckArray[x-1];   
                  else
                     SelectedPuck = PuckArray[PuckCount];
          
                  /* Display the new selection in the PuckName field */
                  PuckName.setText( SelectedPuck.Name );

                  /* Highlight the new selection in the navigation drawer */
                  for ( String Name: PuckNames )
                    if ( Name.equals(SelectedPuck.Name) )
                       PuckList.setItemChecked( PuckNames.indexOf(Name), true );
               }      
            }   
         }   

         /* If the deleted puck is not the last puck */
         if ( Index != PuckCount )
         {
            /* Swap the deleted puck with the last puck */
            Puck Temp = PuckArray[Index];
            PuckArray[Index] = PuckArray[PuckCount];
            PuckArray[PuckCount] = Temp;
         }
      }
      else  /* Puck array is empty */
      { 
         SelectedPuck = null;
         PuckName.setText("");
         PuckName.setEnabled( false );
         PuckName.setVisibility( View.GONE );
         EditButton.setEnabled( false );
         EditButton.setVisibility( View.GONE );
         Spacer.setVisibility( View.GONE );
         SensorHandler.sendEmptyMessage( 0 );
      }

      /* Display toast message for the deleted puck */
      String Text = "Lost " + PuckArray[PuckCount].Name;
      // Toast.makeText( getApplicationContext(), Text, Toast.LENGTH_SHORT ).show();
   }

   /* This is called once a second on the UI thread */
   OnAlarmListener OnPuckTick = new OnAlarmListener()
   {
      @Override 
      public void OnAlarm( STimer source )
      {
         boolean PuckNamesChanged = false;

         /* If the number of pucks has increased within the last second */
         for ( int x=PrevPuckCount; x<PuckCount; x++ )
         {
            /* Display toast message for the new puck */
            // String Text = "Found " + PuckArray[x].Name;
            // Toast.makeText( getApplicationContext(), Text, Toast.LENGTH_SHORT ).show();

            /* Add the puck name to the navigation drawer */
            PuckNames.add( PuckArray[x].Name );
            PuckNamesChanged = true;
         }

         /* Check each puck in the puck array */
         for ( int x=0; x<PuckCount; x++ )
         {
            /* If an advertisement was not received within the last second */
            if ( PuckArray[x].RecvCount == PuckArray[x].PrevCount )
            {
               /* If the puck is idle for too long */
               if ( ++PuckArray[x].IdleCount == MAX_IDLE_COUNT )
               {
                  /* Remove the puck name from the navigation drawer */
                  PuckNames.remove( PuckArray[x].Name );
                  PuckNamesChanged = true;
                  
                  /* Delete the puck */
                  DeletePuck( x-- );
               }
            }   
            else /* An advertisment was received within the last second */
            {
               PuckArray[x].PrevCount = PuckArray[x].RecvCount;   
               PuckArray[x].IdleCount = 0;
            }
         }      

         /* Update the navigation drawer */
         if ( PuckNamesChanged )
            PuckAdapter.notifyDataSetChanged();

         /* Remember the current number of pucks */
         PrevPuckCount = PuckCount;
      }
   };

   /* This is called whenever another part of the app calls SensorHandler.sendEmptyMessage(). */
   /* This routine handles the empty message on the UI thread. */
   /* This routine is the overall fragment manager. */
   private class onSensorMessage implements Handler.Callback
   {
      @Override
      public boolean handleMessage( Message Msg )
      {
         /* If the message is not empty */
         if ( Msg.obj != null )
         {
            /* Get the advertisement from the message */
            Advertisement Adv = (Advertisement)Msg.obj;
         
            /* Process the advertising data */
            for ( int x=0; x<Adv.Data.length && Adv.Data[x]!=0; x+=Adv.Data[x]+1 )
               onAdvertisingData( Adv.Address, Adv.Data[x+1], Arrays.copyOfRange(Adv.Data,x+2,x+Adv.Data[x]+1) );

            return true;      
         }

         MainActivity Main = MainActivity.this;

         /* If switching fragments is in progress, then just exit */
         if ( Main.CurrentMode == PENDING_MODE )
            return true;

         /* If the measurement mode has changed */
         if ( (Main.SelectedPuck == null) ||
              (Main.CurrentMode != Main.SelectedPuck.MeasurementMode) )
         {
            FragmentTransaction Transaction = getFragmentManager().beginTransaction();

            /* Switch to the appropriate fragment */
            if ( Main.SelectedPuck == null )
            {
               Transaction.replace( R.id.container, NotFragment );
            }   
            else if ( Main.SelectedPuck.MeasurementMode == ENVIRONMENTAL_MODE )
            {
               Transaction.replace( R.id.container, EnvFragment );
            }   
            else if ( Main.SelectedPuck.MeasurementMode == BIOMETRIC_MODE )
            {
               Transaction.replace( R.id.container, BioFragment );
            }   

            Transaction.commit();
            
            /* Switching fragments is in progress */
            Main.CurrentMode = PENDING_MODE;
         }

         /* If the first puck has been found */
         if ( Main.SelectedPuck!=null && !Main.PuckName.isEnabled() )
         {
            /* Enable the PuckName text field and display the name of the first puck */
            Main.PuckName.setEnabled( true );
            Main.PuckName.setVisibility( View.VISIBLE );
            Main.EditButton.setEnabled( true );
            Main.EditButton.setVisibility( View.VISIBLE );
            Main.Spacer.setVisibility( View.INVISIBLE );
            Main.PuckName.setText( Main.SelectedPuck.Name );

            /* Highlight the puck name in the navigation drawer */
            Main.PuckList.setItemChecked( 0, true );
         }   

         /* If we are in environment mode then display environmental sensor data */
         if ( Main.CurrentMode == ENVIRONMENTAL_MODE )
            Main.EnvFragment.DisplaySensorData();

         /* If we are in biometric mode then display biometric sensor data */
         if ( Main.CurrentMode == BIOMETRIC_MODE )
            Main.BioFragment.DisplaySensorData();

         return true;
      }
   }

   /* This fragment displays a "not found" message */
   public static class NotFoundFragment extends Fragment 
   {
      private MainActivity Main;

      @Override
      public View onCreateView( LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState ) 
      {
         Main = (MainActivity)getActivity();

         return inflater.inflate( R.layout.fragment_not, container, false );
      }
      
      @Override
      public void onStart()
      {
         super.onStart();

         /* We have now switched to the "not found" fragment */
         Main.CurrentMode = NOT_FOUND_MODE;
      }
   }

   /* This fragment displays environmental sensor data */
   public static class EnvironmentalFragment extends Fragment 
   {
      private MainActivity Main;
      private TextView  Temperature;
      private TextView  Humidity;
      private TextView  AmbientLight;
      private TextView  UV_Index;
      private TextView  Info;
      private TextView  Battery;
      private ImageView F_Button;
      private ImageView C_Button;
      private ImageView InfoImage;
      private LinearLayout InfoTile;

      /* This is called when the environmental fragment is created */
      @Override
      public View onCreateView( LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState ) 
      {
         Main = (MainActivity)getActivity();

         return inflater.inflate( R.layout.fragment_env, container, false );
      }

      /* This is called when the environmental fragment is displayed */
      @Override
      public void onStart()
      {
         super.onStart();

         /* Get handles to the UI controls */
         Temperature  = (TextView)Main.findViewById( R.id.Temperature  );
         Humidity     = (TextView)Main.findViewById( R.id.Humidity     );
         AmbientLight = (TextView)Main.findViewById( R.id.AmbientLight );
         UV_Index     = (TextView)Main.findViewById( R.id.UV_Index     );
         Info         = (TextView)Main.findViewById( R.id.Info         );
         Battery      = (TextView)Main.findViewById( R.id.Battery      );
         F_Button     =(ImageView)Main.findViewById( R.id.F_Button     );
         C_Button     =(ImageView)Main.findViewById( R.id.C_Button     );
         InfoImage    =(ImageView)Main.findViewById( R.id.InfoImage    );
         InfoTile     =(LinearLayout)Main.findViewById( R.id.InfoTile  );

         /* Register a listener for handling F/C button clicks */
         F_Button.setOnClickListener( OnTempScaleClick );
         C_Button.setOnClickListener( OnTempScaleClick );

         /* Register a listener for handling info tile clicks */
         InfoTile.setOnClickListener( OnInfoTileClick );

         /* We have now switched to the enviromental fragment */
         Main.CurrentMode = ENVIRONMENTAL_MODE;
      }

      /* This is called when the user taps on the F/C button */
      final OnClickListener OnTempScaleClick = new OnClickListener()
      {
         public void onClick( final View v )
         {
            /* Toggle the temperature scale between Fahrenheit and Celcius */
            if ( Main.Fahrenheit )
            {
               Main.Fahrenheit = false;
               F_Button.setImageResource( R.drawable.btn_fahrenheit_normal );
               C_Button.setImageResource( R.drawable.btn_celsius_pressed );
               Temperature.setText( String.format("%.1f °C", Main.SelectedPuck.Temperature ));
            }   
            else
            {
               Main.Fahrenheit = true;   
               F_Button.setImageResource( R.drawable.btn_fahrenheit_pressed );
               C_Button.setImageResource( R.drawable.btn_celsius_normal );
               float Fahrenheit = (Main.SelectedPuck.Temperature * 9)/5 + 32;
               Temperature.setText( String.format("%.1f °F", Fahrenheit ));
            }   
         }
      };

      /* This is called when the user taps on the info tile */
      final OnClickListener OnInfoTileClick = new OnClickListener()
      {
         public void onClick( final View v )
         {
            Intent intent = new Intent( Main, InfoActivity.class );
            startActivity( intent );
         }
      };

      /* Display environmental sensor data */
      public void DisplaySensorData()
      {
         /* Display the temperature in Fahrenheit or Celsius */
         if ( Main.Fahrenheit )
         {
            float Fahrenheit = (Main.SelectedPuck.Temperature * 9)/5 + 32;
            Temperature.setText( String.format("%.1f °F", Fahrenheit ));
         }
         else Temperature.setText( String.format("%.1f °C", Main.SelectedPuck.Temperature ));

         /* Display humidity and and ambient light */
         Humidity.setText(     String.format("%.1f %%", Main.SelectedPuck.Humidity     ));
         AmbientLight.setText( String.format("%d lux" , Main.SelectedPuck.AmbientLight ));

         /* Adjust the text size based on the number of digits to display */
         if ( Main.SelectedPuck.AmbientLight < 1000 )
            AmbientLight.setTextSize( 38 );
         else   
            AmbientLight.setTextSize( 32 );

         /* Display the UV Index */
         UV_Index.setText( String.format("%d",Main.SelectedPuck.UV_Index) );
         
         /* If the battery voltage is good */
         if ( Main.SelectedPuck.Battery >= 2.7 )
         {
            if ( Battery.getVisibility() != View.GONE )
            {
               Battery.setVisibility( View.GONE );
               InfoImage.setImageResource( R.drawable.ic_info );
               Info.setText("Tap here to see additional information");   
            }
         }
         else /* The battery voltage is low */
         {
            if ( Battery.getVisibility() == View.GONE )
            {
               InfoImage.setImageResource( R.drawable.ic_battery );
               Info.setText("Battery");
               Battery.setText("Low");
            }

            /* Blink the word "Low" */
            if ( Battery.getVisibility() == View.VISIBLE )
               Battery.setVisibility( View.INVISIBLE );
            else   
               Battery.setVisibility( View.VISIBLE );
         }
      }
   }

   /* This fragment displays biometric sensor data */
   public static class BiometricFragment extends Fragment 
   {
      private MainActivity Main;
      private TextView HeartRate;
      private XYPlot   Graph;
      private SimpleXYSeries Line;
      private int PrevDelta = 0;
      private int MaxDelta  = 0;
      private int Gain      = 1;
      private static final int BPF_ORDER = 4;
      private static final int BPF_FILTER_LEN = BPF_ORDER * 2 + 1;
      private short[]  BPF_In  = {0,0,0,0,0,0,0,0,0};
      private double[] BPF_Out = {0,0,0,0,0,0,0,0,0};

      private double[] BPF_a = 
      { //Fs=25; [BPF_b,BPF_a] = butter(4,[45 200]/60/Fs*2);
         1.000000000000000e+000,
        -5.805700439644110e+000,
         1.514036628292202e+001,
        -2.323300817159229e+001,
         2.298582338785502e+001,
        -1.502165263561143e+001,
         6.331004788861760e+000,
        -1.573336063098673e+000,
         1.767891944741809e-001
      };
        
      private double[] BPF_b =
      {
         5.392924554970057e-003,
         0,
        -2.157169821988023e-002,
         0,
         3.235754732982035e-002,
         0,
        -2.157169821988023e-002,
         0,
         5.392924554970057e-003
      };

      /* This is called when the biometric fragment is created */
      @Override
      public View onCreateView( LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState ) 
      {
         Main = (MainActivity)getActivity();
   
         return inflater.inflate( R.layout.fragment_bio, container, false );
      }
      
      /* This is called when the biometric fragment is displayed */
      @Override
      public void onStart()
      {
         super.onStart();

         /* Get handles to the UI controls */
         HeartRate = (TextView)Main.findViewById( R.id.HeartRate );
         Graph     = (XYPlot)Main.findViewById( R.id.Graph );

         /* Create a line to display on the graph */
         Line = new SimpleXYSeries("Heart Beat");
         Line.useImplicitXVals();

         /* Configure the graph */
         Graph.clear();
         Graph.setRangeBoundaries(  0, GRAPH_RANGE_SIZE,  BoundaryMode.FIXED );
         Graph.setDomainBoundaries( 0, GRAPH_DOMAIN_SIZE, BoundaryMode.FIXED );
         Graph.addSeries( Line, new LineAndPointFormatter(Color.RED,Color.RED,null,null) );
         Graph.getLegendWidget().setVisible( false );
         Graph.getBackgroundPaint().setColor( Color.TRANSPARENT );
         Graph.getGraphWidget().getBackgroundPaint().setColor( Color.TRANSPARENT );
         Graph.getGraphWidget().getDomainLabelPaint().setColor( Color.TRANSPARENT );
         Graph.getGraphWidget().getRangeLabelPaint().setColor( Color.TRANSPARENT );
         Graph.setBorderStyle( BorderStyle.NONE, 0f, 0f );
         Graph.getGraphWidget().getGridBackgroundPaint().setColor( Color.WHITE ); 
         
         /* We have now switched to the biometric fragment */
         Main.CurrentMode = BIOMETRIC_MODE;
      }

      /* Band pass filter */
      private short BPF_FilterProcess( int raw_value )
      {
          //BPF: [BPF_b,BPF_a] = butter(4,[60 300]/60/Fs*2);
          /*
          The filter is a "Direct Form II Transposed" implementation of the standard difference equation:
          a(1)*y(n) = b(1)*x(n) + b(2)*x(n-1) + ... + b(nb+1)*x(n-nb)
                                - a(2)*y(n-1) - ... - a(na+1)*y(n-na)
          */

          /* Shift the BPF in/out data buffers and add the new input sample */
          for ( int i=BPF_FILTER_LEN-1; i>0; i-- )
          {
              BPF_In[i]  = BPF_In[ i-1];
              BPF_Out[i] = BPF_Out[i-1];
          }

          /* Add the new input sample */
          BPF_In[0]  = (short)raw_value;            
          BPF_Out[0] = 0;

          /* a(1)=1, y(n) = b(1)*x(n) + b(2)*x(n-1) + ... + b(nb+1)*x(n-nb) */
          for ( int j=0; j<BPF_FILTER_LEN; j++ )    
              BPF_Out[0] += BPF_b[j] * BPF_In[j];

          /* y =y(n)- a(2)*y(n-1) - ... - a(na+1)*y(n-na) */
          for ( int j=1; j<BPF_FILTER_LEN; j++ )    
              BPF_Out[0] -= BPF_a[j] * BPF_Out[j];

          return (short)(BPF_Out[0] + 0.5); //0.5=roundup
      }
      
      /* Display the HRM samples on the graph */
      public void DisplaySamples( int[] HRM_Sample )
      {
         int Delta;
         int AbsDelta;
         
         for ( int Sample : HRM_Sample )
         {
            if ( Line.size() > GRAPH_DOMAIN_SIZE )
               Line.removeFirst();

            if ( Main.SelectedPuck.HRM_State == HRM_STATE_ACTIVE )
            {
               /* Get the delta from the band pass filter */
               Delta = BPF_FilterProcess( Sample );
               
               /* Find the absolute value of the delta */
               if ( Delta > 0 )
                  AbsDelta =  Delta;
               else
                  AbsDelta = -Delta;   
               
               /* Find the maximum delta for the cycle */
               if ( AbsDelta > MaxDelta )
                  MaxDelta = AbsDelta;
                  
               /* Adjust the gain once per cycle when crossing the x axis */
               if ( PrevDelta<0 && Delta>0 )
               {
                  if ( MaxDelta > 2000 )
                     Gain = 4;               /* Burst:             >2000 */
                  else if ( MaxDelta > 1000 )
                     Gain = 10;              /* High:       1000 to 2000 */
                  else if ( MaxDelta > 200 )
                     Gain = 20;              /* Normal-high: 200 to 1000 */
                  else if ( MaxDelta > 20 )
                     Gain = 100;             /* Normal-low:   20 to 200  */
                  else  
                     Gain = 500;             /* Low:                 <20 */
                  /* Gain = 10000 / MaxDelta; */
                     
                  MaxDelta = 0;
               }   

               /* Note the previous delta */
               PrevDelta = Delta; 

               /* Add the amplified delta to the end of the line */
               Line.addLast( null, (Delta*Gain)+(GRAPH_RANGE_SIZE/2) );
            }   
            else Line.addLast( null, GRAPH_RANGE_SIZE/2 );
         }
      }

      /* Display biometric sensor data */
      public void DisplaySensorData()
      {
         /* Display the state of the heart rate monitor and the heart rate */
         switch ( Main.SelectedPuck.HRM_State )
         {
            case HRM_STATE_IDLE:      HeartRate.setText("Idle");      break;
            case HRM_STATE_NOSIGNAL:  HeartRate.setText("No Signal"); break;
            case HRM_STATE_ACQUIRING: HeartRate.setText("Acquiring"); break;
            case HRM_STATE_ACTIVE:    HeartRate.setText( String.format("%d bpm", 
                                       Main.SelectedPuck.HRM_Rate )); break;
            case HRM_STATE_INVALID:   HeartRate.setText("Re-position Finger"); break;
            case HRM_STATE_ERROR:     HeartRate.setText("Error");     break;
         }

         /* If advertisements were lost */
         if ( Main.SelectedPuck.LostAdv > 0 )
         {
            /* Calculate the number of lost samples */
            int LostSamples = Main.SelectedPuck.LostAdv * HRM_SAMPLE_COUNT;
            
            /* Create an array for holding filler values for the lost samples */
            int[] Filler = new int[LostSamples];

            /* Replace the lost samples with filler values */
            int Step = (Main.SelectedPuck.HRM_Sample[0] - Main.SelectedPuck.HRM_PrevSample) / 
               (LostSamples + 1);
            int Sample = Main.SelectedPuck.HRM_PrevSample + Step;
            for ( int x=0; x<LostSamples; Sample+=Step,x++ )
               Filler[x] = Sample;
      
            /* Display the filler values on the graph */
            DisplaySamples( Filler );
         }

         /* Display the HRM samples on the graph */
         DisplaySamples( Main.SelectedPuck.HRM_Sample );

         /* Redraw the graph */
         Graph.redraw();
      }
   }

   /* This is called when a BLE advertisement is received from any BLE device */
   private BluetoothAdapter.LeScanCallback ScanCallback = new BluetoothAdapter.LeScanCallback() 
   {
      @Override
      public void onLeScan( final BluetoothDevice device, int rssi, byte[] scanRecord ) 
      {
         /* Create an advertisement object */
         Advertisement Adv = new Advertisement();
         Adv.Address = device.getAddress();
         Adv.Data    = scanRecord;

         /* Send the advertisement to the sensor handler */
         Message Msg = Message.obtain();
         Msg.obj = Adv;
         SensorHandler.sendMessage( Msg );
      }
   };
   
   /* Process advertising data */
   void onAdvertisingData( String Address, byte ADType, byte ADData[] )
   {
      /* If the advertisement contains Silabs manufacturer specific data */
      if ( (ADType==(-1)) && ((ADData[0]==0x34)||(ADData[0]==0x35)) && (ADData[1]==0x12) )
      {
         /* Find the puck with this Bluetooth address */
         Puck ThisPuck = FindPuck( Address );
         if ( ThisPuck == null ) return;

         /* If its an old style advertisement */
         if ( ADData[0] == 0x34 )
         {
            /* Process the sensor data */
            for ( int x=2; x<ADData.length; x+=ADData[x]+1 )
               onSensorData( ThisPuck, ADData[x+1], Arrays.copyOfRange(ADData,x+2,x+ADData[x]+1) );
         }      

         /* If its a new style advertisement */
         if ( ADData[0] == 0x35 )
         {
            /* If its an environmental advertisement then process it */
            if ( ADData[2] == ENVIRONMENTAL_MODE )
               onEnvironmentalData( ThisPuck, Arrays.copyOfRange(ADData,3,14) ); 

            /* If its a biometric advertisement then process it */
            if ( ADData[2] == BIOMETRIC_MODE )
               onBiometricData( ThisPuck, Arrays.copyOfRange(ADData,3,18) ); 
         }   

         /* Another adverstisement has been received */
         ThisPuck.RecvCount++;

         /* Ignore duplicate advertisements */
         if ( ThisPuck.Sequence != ThisPuck.PrevSequence )
         {
            /* Another unique adverstisement has been received */
            ThisPuck.UniqueCount++;
            
            /* Calculate the number of lost advertisements */
            if ( ThisPuck.Sequence > ThisPuck.PrevSequence )
               ThisPuck.LostAdv = ThisPuck.Sequence - ThisPuck.PrevSequence - 1;
            else /* Wrap around */
               ThisPuck.LostAdv = ThisPuck.Sequence - ThisPuck.PrevSequence + 255;

            /* Big losses means just found a new puck */   
            if ( (ThisPuck.LostAdv == 1) || (ThisPuck.LostAdv == 2) )
               ThisPuck.LostCount += ThisPuck.LostAdv;
               
            ThisPuck.PrevSequence = ThisPuck.Sequence;      

            /* Display new sensor data for the selected puck */
            if ( ThisPuck == SelectedPuck )
               SensorHandler.sendEmptyMessage( 0 );
         }
      }
   }

   /* Process sensor data (old style advertisement) */
   void onSensorData( Puck ThisPuck, byte SDType, byte SDData[] )
   {
      int Value;
      
      if ( SDData.length == 2 )
         Value = Int16( SDData[0], SDData[1] );
      else
         Value = Int8( SDData[0] );   

      switch ( SDType )
      {
         case SD_MODE:
            ThisPuck.MeasurementMode = Value;
            break;
         case SD_SEQUENCE:
            ThisPuck.Sequence = Value;
            break;
         case SD_HUMIDITY:
            ThisPuck.Humidity = ((float)Value)/10;
            break;
         case SD_TEMPERATURE:
            ThisPuck.Temperature = ((float)Value)/10;
            break;
         case SD_AMB_LIGHT:
            ThisPuck.AmbientLight = Value*2;
            break;
         case SD_UV_LIGHT:
            ThisPuck.UV_Index = Value;
            break;
         case SD_BATTERY:
            ThisPuck.Battery = ((float)Value)/10;
            break;
         case SD_HRM_STATE:
            ThisPuck.HRM_State = Value;
            break;
         case SD_HRM_RATE:
            ThisPuck.HRM_Rate = Value;
            break;
         case SD_HRM_SAMPLE:
            ThisPuck.HRM_PrevSample = ThisPuck.HRM_Sample[HRM_SAMPLE_COUNT-1];
            for ( int x=0; x<HRM_SAMPLE_COUNT; x++ )
               ThisPuck.HRM_Sample[x] = Int16( SDData[x*2], SDData[(x*2)+1] );
            break;
      }
   } 
   
   /* Process environmental data (new style advertisement) */
   void onEnvironmentalData( Puck ThisPuck, byte Data[] )
   {
      ThisPuck.MeasurementMode = ENVIRONMENTAL_MODE;
      ThisPuck.Sequence        = Int8( Data[0] );
      ThisPuck.Humidity        = ((float)Int16(Data[3],Data[4]))/10;
      ThisPuck.Temperature     = ((float)Int16(Data[5],Data[6]))/10;
      ThisPuck.AmbientLight    = Int16( Data[7], Data[8] )*2;
      ThisPuck.UV_Index        = Int8( Data[9] );
      ThisPuck.Battery         = ((float)Int8(Data[10]))/10;
   }  

   /* Process biometric data (new style advertisement) */
   void onBiometricData( Puck ThisPuck, byte Data[] )
   {
      ThisPuck.MeasurementMode = BIOMETRIC_MODE;
      ThisPuck.Sequence        = Int8( Data[0] );
      ThisPuck.HRM_State       = Int8( Data[3] );
      ThisPuck.HRM_Rate        = Int8( Data[4] );

      ThisPuck.HRM_PrevSample = ThisPuck.HRM_Sample[HRM_SAMPLE_COUNT-1];
      for ( int x=0; x<HRM_SAMPLE_COUNT; x++ )
         ThisPuck.HRM_Sample[x] = Int16( Data[5+(x*2)], Data[6+(x*2)] );
   }  

   /* Convert byte to int */
   int Int8( byte Data )
   {
      return (int)(((char)Data)&0xFF);
   }

   /* Convert two bytes to int */
   int Int16( byte LSB, byte MSB )
   {
      return Int8(LSB) + (Int8(MSB)*256);
   }

}

