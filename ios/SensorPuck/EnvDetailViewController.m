//
//  EnvDetailViewController.m
//  BlueCenter
//
//  Created by Lets nurture on 15/10/14.
//  Copyright (c) 2014 Lets nurture. All rights reserved.
//

#import "EnvDetailViewController.h"
#import "AppDelegate.h"

#define DeviceInformationUDID @"180A"

#define IS_IPAD (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPad)
#define IS_IPHONE (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPhone)
#define IS_RETINA ([[UIScreen mainScreen] scale] >= 2.0)

#define SCREEN_WIDTH ([[UIScreen mainScreen] bounds].size.width)
#define SCREEN_HEIGHT ([[UIScreen mainScreen] bounds].size.height)
#define SCREEN_MAX_LENGTH (MAX(SCREEN_WIDTH, SCREEN_HEIGHT))

#define IS_IPHONE_4_OR_LESS (IS_IPHONE && SCREEN_MAX_LENGTH < 568.0)
#define IS_IPHONE_5 (IS_IPHONE && SCREEN_MAX_LENGTH == 568.0)
#define IS_IPHONE_6 (IS_IPHONE && SCREEN_MAX_LENGTH == 667.0)
#define IS_IPHONE_6P (IS_IPHONE && SCREEN_MAX_LENGTH == 736.0)



#define MAX_PUCK_COUNT 16
#define HRM_SAMPLE_COUNT 5
const int IDLE = 0;
const int NO_SIGNAL = 1;
const int ACURING = 2;
const int ACTIVE = 3;
const int INVALID = 4;
const int ERROR = 5;





@interface EnvDetailViewController ()
{
    //*********** Local variables
    AppDelegate     *appdelegate; // instance of appdelegate to use application variable
    BOOL            isFirstTime;   // boolean to check is application ran first time
    NSArray         *PuckDetail;
    
    
    NSTimer         *TickTimer;
    NSMutableArray  *puckNames;
    
    CBPeripheral    *SelectePeripheral;    // Peripjeral object to keep selected sensor
    
    NSString        *HRMLData;
    
    float           CurrentTemprature;    // contain last discovered temrature
    BOOL            isCurrentTempratureF;  // boolean to check is last temprature discoverd was fahrenheit
    
    //----- Timer for discovery
    NSTimer         *discoveryTimer;
    int             DiscoverySecondsLeft;
    
}
@property (nonatomic, readwrite, strong) NSMutableArray *plotData;
@property (nonatomic, readwrite, assign) NSUInteger     currentIndex;
@property (nonatomic, readwrite, strong) NSTimer        *dataTimer;

@end

@implementation EnvDetailViewController
@synthesize PuckArray;

#pragma mark - View Lifecycle
-(void)viewWillDisappear:(BOOL)animated
{
    //------------- Invalidate all timers
    if ([timer isValid]) {
        [timer invalidate];
    }
    if([discoveryTimer isValid])
    {
        [discoveryTimer invalidate];
    }
    //----------------------------------------
}
-(void)viewWillAppear:(BOOL)animated
{
    //------------- Set Top navigation Bar hidden
    [self.navigationController setNavigationBarHidden:YES];
    //-----------------------------------------------------------
    
    //------------ set Posiotions of each item in UIView
    [EnvDetailView setFrame:CGRectMake(0, 0, scrollview.frame.size.width, [UIScreen mainScreen].bounds.size.height-scrollview.frame.origin.y)];
    [BiometricView setFrame:CGRectMake(0, 0, scrollview.frame.size.width, [UIScreen mainScreen].bounds.size.height-scrollview.frame.origin.y)];
    [self SetViewLayout];
    //-------------------------------------------------------
    
    
    //----------- Puck array contains list of sensors, Initialize puck array
    PuckArray = [[NSMutableArray alloc] init];
    //--------------------------------------------
    
    //----- Central manager of BLE, initialization and asigning delegate to central
    self.manager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
    //-------------------------------------------------------------------------
    
    //---------- Setting side menu of list of pucks in listview i.e tableview
    ListTableView.frame =  CGRectMake(0, 0.0, 0.0, self.view.frame.size.height-0);
    [ListTableView setBackgroundColor:[UIColor colorWithRed:77.0/255.0 green:77.0/255.0 blue:77.0/255.0 alpha:1.0]];
    
    [self.view bringSubviewToFront:ListTableView];
    //---------------------------------------------------------

    //--------- initialization for textfield of puck name
    puckNameTextField.enabled = NO;
    //-------------------------------
    
    //---------- Global variable in appdelegate
    appdelegate.isConnected = false;
    //------------------------------------
    
    //------ discovery timer, this timer call updatediscovery timer. This checks if no sensor discoverd for 12 seconds it will rnavigate to no puck screen
    discoveryTimer = [NSTimer scheduledTimerWithTimeInterval:2.0f target:self selector:@selector(UpdateDiscoveryTimer) userInfo:nil repeats:YES];
    
}


- (void)viewDidLoad {
    [super viewDidLoad];
    
    
    // Do any additional setup after loading the view.
    appdelegate = (AppDelegate *) [[UIApplication sharedApplication] delegate];
    //------- make aspectfit image of button - edit puckname
    [EditPuckButton.imageView setContentMode:UIViewContentModeScaleAspectFit];
    //------------------------------------------------------------
    
    
    //------- F/C button
    Farehnheit = false;
    [FarehnheitButton setSelected:NO];
    [CelsiusButton setSelected:YES];
    //-------------------------------
    
    //------ setup leftbar button
    [self setupMenuBarButtonItems];
    //---------------------------
    
    
    //---------- Add gestures for sidetable view - list of pucks
    UISwipeGestureRecognizer *gesture = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(HideMenuonSwipe)];
    [gesture setDirection:UISwipeGestureRecognizerDirectionLeft];
    [self.view addGestureRecognizer:gesture];
    
    UISwipeGestureRecognizer *gesture1 = [[UISwipeGestureRecognizer alloc] initWithTarget:self action:@selector(ShowMenuonSwipe)];
    [gesture1 setDirection:UISwipeGestureRecognizerDirectionRight];
    [self.view addGestureRecognizer:gesture1];
    //---------------------------------------------------------------
    
    
}
#pragma mark -

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

#pragma mark - Count down -
-(void)UpdateDiscoveryTimer
{
    NSLog(@"Discovery Count %d",DiscoverySecondsLeft);
    if(DiscoverySecondsLeft > 0 ){
        DiscoverySecondsLeft -- ;
    }
    else
    {
        NSLog(@"Update Discovery");
        [self RemoveView];
        [PuckArray removeAllObjects];
        [ListTableView reloadData];
    }
    
}
#pragma mark -
// This method update UI values from data of sensor
-(void)setValueToViewWithData:(NSData *)data
{
    [EditPuckContainerView setHidden:NO];
    [puckNameTextField setEnabled:YES];
    appdelegate.isConnected = true; // update global variable to yes, device is connected to sensor
    
    @try {  // Try block starting
        const uint8_t *bytePtr = (const uint8_t*)[data bytes];
        //--------- Following is loop of data discoved from sensor, uncomment to see each data value
        
    
//
//        NSInteger totalData = [data length];
//        for (int i = 0 ; i < totalData; i ++)
//        {
//            if (i==12) {
//                NSLog(@"UV Char %c",bytePtr[i]);
//            }
//            NSLog(@"data byte chunk%d : %d",i, bytePtr[i]);
//        }
        //---------------------------


    // Check in which mode sensor discovered data, bioemtric of environment
    int t = bytePtr[2];
        // if t's value 0 then sensor is in environmental mode
    if (t==0) { // envioronmental condtion true
        if ([BiometricView superview]) {
            [BiometricView removeFromSuperview];
        }
        if (![EnvDetailView superview]) {
            [scrollview addSubview:EnvDetailView];
        }

        //============== Humidity
        uint16_t humidity = bytePtr[6];
        
        humidity += (bytePtr[7])*256;
        
        float h = humidity/(float)10;
        NSString *s= @"%"; 
        
        [HumidityLabel setText:[NSString stringWithFormat:@"%.1f %@",h,s]];
        //========================================
        
        //================== Temprature
        int16_t Temprature1 = bytePtr[8];
        
        Temprature1 += (bytePtr[9])*256;
        
        float t = Temprature1/(float)10;
        
        float temprature = ceilf(t * 100) / 100;
        
        //------------------- Farehnheit
        if (Farehnheit)
        {
            float Fahrenheitf = (temprature * 9)/5 + 32;
            isCurrentTempratureF = true;
            CurrentTemprature = Farehnheit;
            [TempratureLabel setText:[NSString stringWithFormat:@"%.1f °F",Fahrenheitf]];
        }
        else
        {   //-------------------- Celcious
            [TempratureLabel setText:[NSString stringWithFormat:@"%.1f °C",temprature]];
            isCurrentTempratureF = false;
            CurrentTemprature = temprature;
        }

        //===============================================
        
        
        //====================== SD AM Light
        int32_t light1 = bytePtr[10];
        
        light1 += (bytePtr[11])*256;
        float l = light1*2;
        
        float light = ceilf(l * 100) / 100;
        if (light<0) {
            light=0;
        }
        [AmbientLightLabel setText:[NSString stringWithFormat:@"%.0f lux",light]];
        

        //=================================================
        
        //====================== UV Light
        float uvlight = bytePtr[12];
        
        [UVLightLabel setText:[NSString stringWithFormat:@"%.0f",uvlight]];
        
        int uvint = uvlight;
        
        [self SEtUVImageWithInt:uvint];
        NSLog(@"UV Light %f",uvlight);
        
        //=================================================
        
        //====================== Battery
        int8_t battery1 = bytePtr[13];
       
        float bt = battery1/(double)10;

        if (bt<2.7) {
            [BatteryLabel setText:@"Low"];
            [BatteryImageView setImage:[UIImage imageNamed:@"battery_low.png"]];
        }
        else
        {
            [BatteryLabel setText:@"Good"];
            [BatteryImageView setImage:[UIImage imageNamed:@"battery.png"]];
        }
        
        //=================================================

        if (!isEditing) {
            puckNameTextField.text = appdelegate.SelectedPuck.Name;
        }
        HeartRateLabel.text = @"Idle";
        
    } // env if completes here
    else
    {   // environmental condition false and in biometric mode
        int HRMState =  bytePtr[6];
        //----- Get status of HRM and display value based on status
        switch (HRMState) { //switch start
        case IDLE:
        {
            HeartRateLabel.text = @"Idle";
        }
            break;
        case NO_SIGNAL:
        {
            HeartRateLabel.text = @"No Signal";
        }
            break;
        case ACURING:
        {
            HeartRateLabel.text = @"Acquiring";

        }
            break;
        case ACTIVE:
        {
            int sample = bytePtr[7];
            HeartRateLabel.text = [NSString stringWithFormat:@"%d bpm",sample];

        }
            break;
        case INVALID:
        {
            NSLog(@"INVALID");
            HeartRateLabel.text = @"Re-position finger";
        }
                break;
        case ERROR:
        {
            NSLog(@"ERROR");
            HeartRateLabel.text = @"Error";
        }
                break;
                
        default:
                break;
        } // Switch ending
    }// Else condition closing, biometric block end
    } // @try closing
    @catch (NSException *exception) {
        [EnvDetailView removeFromSuperview];
    }
    @finally {
        
    }

}
#pragma mark -
//following method update image based on UV value
-(void)SEtUVImageWithInt:(int)uv
{
    if (uv<=0) {
        [UVImageView setHidden:YES];
    }
    else
    {
        [UVImageView setHidden:NO];
        NSString *imagename = [NSString stringWithFormat:@"UV%d.png",uv];
        [UVImageView setImage:[UIImage imageNamed:imagename]];
    }
}
//Following method handles views and if no sensor discovered it will display no sensor screen
-(void)RemoveView
{
    [EditPuckContainerView setHidden:YES];
    NSLog(@"********** COUNTER FIRED**********");
    [puckNameTextField setEnabled:NO];
    if ([EnvDetailView superview]) {
        [EnvDetailView removeFromSuperview];
    }
    if([BiometricView superview])
    {
        [BiometricView removeFromSuperview];
    }
    puckNameTextField.text= @"";
    appdelegate.isConnected = false;
    appdelegate.SelectedPuck.Address = nil;
    [[NSNotificationCenter defaultCenter] postNotificationName: @"levelDetails" object:nil];
}
#pragma mark - central Manager Delegate
// following method scan for sensor at each one minute - timer
-(void)AsktoSacnForPeripheral
{
    NSLog(@"Scan Start Again");
    [self.manager scanForPeripheralsWithServices:nil options:@{CBCentralManagerScanOptionAllowDuplicatesKey : @YES }];
}
// Follwoing methos gets divice's state for BLE and if it is comptaible with BLE and is on then it will fire timer  for scanining sensor at each minute
- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    [EnvDetailView removeFromSuperview];
    switch (central.state) {
        case CBCentralManagerStatePoweredOn:
        {
            NSArray *services = @[[CBUUID UUIDWithString:DeviceInformationUDID]];
            CBCentralManager *centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
            [centralManager scanForPeripheralsWithServices:services options:nil];
            [self.manager scanForPeripheralsWithServices:nil options:@{CBCentralManagerScanOptionAllowDuplicatesKey : @YES }];
            
            //-------- Timer To scan again for peripherals after 1 mins
            TickTimer = [NSTimer scheduledTimerWithTimeInterval:60.0 target:self selector:@selector(AsktoSacnForPeripheral) userInfo:nil repeats:YES];
        
        }
            break;
        case CBCentralManagerStatePoweredOff:
        {
            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Bluetooth on this device is currently powered off." message:@"" delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
            [alert show];
        }
            break;
        case CBCentralManagerStateUnauthorized:
        {
            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"This app is not authorized to use Bluetooth Low Energy." message:@"" delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
            [alert show];
        }
            break;
        case CBCentralManagerStateUnsupported:
        {
            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"This device does not support Bluetooth Low Energy." message:@"" delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
            [alert show];
        }
        default:
            NSLog(@"Central Manager did change state");
            break;
    }
}
- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advertisementData RSSI:(NSNumber *)RSSI {
    
    int isconnectable = [[advertisementData valueForKey:@"kCBAdvDataIsConnectable"] intValue];
    
    if (isconnectable==0)
    {
        //update variable for timer of 12 seconds
        DiscoverySecondsLeft = 6;
        
        SelectePeripheral = peripheral;
        // get advertise data
        NSData *data1 =[advertisementData valueForKey:@"kCBAdvDataManufacturerData"];
        
        if (peripheral.name==nil) {
            NSData *data = data1;
            if (data!=nil) {
                dispatch_queue_t myQueue = dispatch_queue_create("My Queue",NULL);
                dispatch_async(myQueue, ^{
                    // Perform long running process
                    
                    dispatch_async(dispatch_get_main_queue(), ^{
                        // Update the UI
                        NSString *Address = [self getPuckNameWithData:data];
                        
                        [self SavePuck:Address WithData:data];
                        
                    });
                });

            }
        }
    }
}
-(void)centralManager:(CBCentralManager *)central didRetrievePeripherals:(NSArray *)peripherals
{
    NSLog(@"Periperals");
}
#pragma mark - Muliple Pucks
-(void)SavePuckWithArray:(NSArray *)arr
{
    NSString *address = [arr objectAtIndex:0];
    NSData *mdata = [arr objectAtIndex:1];
    [self SavePuck:address WithData:mdata];
}
-(void)SavePuck:(NSString *)Address WithData:(NSData *)data
{
    NSString *puckname = [[NSUserDefaults standardUserDefaults] valueForKey:Address];
    
    if (puckname==nil || [puckname isEqualToString:@""]) {
        puckname = Address;
    }
    //---- If new sensor is discovered add it in array
    if ([self ISPuckUnique:Address] && Address!=nil) {
            Puck *puck = [[Puck alloc] init];
            puck.Address = Address;
            puck.Name = puckname;
            puck.identifier = SelectePeripheral.identifier;
            puck.isRemoved = 0;
            puck.lastDiscoveryTime = [NSDate date];
            [PuckArray addObject:puck];
            [ListTableView reloadData];
    }
    else
    {   // if existing sensor discovered updte its value
        int LimitForIdle = 10;
        if (PuckArray.count>3) {
                LimitForIdle = 14;
        }
        if (PuckArray.count>5) {
            LimitForIdle = 17;
        }
        if (PuckArray.count>8) {
            LimitForIdle = 20;
        }
        if (PuckArray.count>10) {
            LimitForIdle = 23;
        }
        for (int i=0; i<PuckArray.count; i++) {
            Puck *puck = [PuckArray objectAtIndex:i];
            if ([puck.Address isEqualToString:[self getPuckNameWithData:data]]) {
                puck.RecvCount++;
                puck.IdleCount = 0;
                    
            }
            else
            {
                puck.IdleCount++;
                NSLog(@"Puckname:%@ iDleCounnt%d",puck.Name,puck.IdleCount);
                if (puck.IdleCount>=LimitForIdle) {
                    puck.isRemoved = 1;
                    if (puck.IdleCount>=(LimitForIdle+3)) {
                        NSDate* date1 = puck.lastDiscoveryTime;
                        NSDate* date2 = [NSDate date];
                        NSTimeInterval distanceBetweenDates = [date2 timeIntervalSinceDate:date1];
                        if (distanceBetweenDates>3) {

                            [PuckArray removeObjectAtIndex:i];
                            appdelegate.SelectedPuck = [PuckArray objectAtIndex:0];
                            puckNameTextField.text = appdelegate.SelectedPuck.Name;
                        }
                    }
                        
                    [ListTableView reloadData];
                }

            }
        }
    }
    //---- If first time first sensor discovered
    if (appdelegate.SelectedPuck.Address==nil || [appdelegate.SelectedPuck.Address isEqualToString:@""]) {
        appdelegate.SelectedPuck = [PuckArray objectAtIndex:0];
        puckNameTextField.text = appdelegate.SelectedPuck.Name;
        dispatch_queue_t myQueue = dispatch_queue_create("My Queue2",NULL);
        dispatch_async(myQueue, ^{
            // Perform long running process
            
            dispatch_async(dispatch_get_main_queue(), ^{
                // Update the UI
                [self setValueToViewWithData:data];
                
            });
        });
        
    }
    else
    {  //---- If discovered sensor is selected sensor update UI

        if ([Address isEqualToString:appdelegate.SelectedPuck.Address]) {
            selectedpuckUUID = SelectePeripheral.identifier;
            dispatch_queue_t myQueue = dispatch_queue_create("My Queue2",NULL);
            dispatch_async(myQueue, ^{
                // Perform long running process
                
                dispatch_async(dispatch_get_main_queue(), ^{
                    // Update the UI
                    [self setValueToViewWithData:data];
                    
                });
            });

        }
    }
    
}
//---- Method to check sensor is aleardy in array or new
-(BOOL)ISPuckUnique:(NSString *)Address
{
    int Flag=0;
    for (int i=0; i<PuckArray.count; i++) {
        Puck *puck = [PuckArray objectAtIndex:i];
        if ([puck.Address isEqualToString:Address]) {
            Flag = 1;
            break;
        }
    }
    if (Flag==1)
        return false;
    else
        return true;
}
//----- get sensor name from advertise of sensor
-(NSString *)getPuckNameWithData:(NSData *)PuckData
{
    NSString *puckname = @"";
    @try {
        const uint8_t *bytePtr = (const uint8_t*)[PuckData bytes];
        
        NSString *PuckAddress = [NSString stringWithFormat:@"%02x%02x",bytePtr[5],bytePtr[4]];
        puckname = [NSString stringWithFormat:@"Puck_%@",[PuckAddress uppercaseString]];
    }
    @catch (NSException *exception) {
        
    }
    @finally {
        
    }
    return puckname;
}

#pragma mark -
#pragma mark -
//---- Method to check is application just run
-(BOOL)isFirstTime
{
    PuckDetail = [[NSUserDefaults standardUserDefaults] valueForKey:@"Pucks"];
    if (PuckDetail.count<=0) {
        isFirstTime = true;
    }
    else
        isFirstTime = false;
    return isFirstTime;

}
#pragma mark -
#pragma mark - IBAction - F/C and Pencil
-(IBAction)FarehnheitPressed:(id)sender
{
    [CelsiusButton setSelected:NO];
    [FarehnheitButton setSelected:YES];
    if (!Farehnheit) {
        Farehnheit = true;
        float temprature =[TempratureLabel.text floatValue];
        float Fahrenheitf = (temprature * 9)/5 + 32;
        [TempratureLabel setText:[NSString stringWithFormat:@"%.1f °F",Fahrenheitf]];
        NSLog(@"Temprature is %f",Fahrenheitf);
    }

    
}
-(IBAction)CelciousPressed:(id)sender
{
    [CelsiusButton setSelected:YES];
    [FarehnheitButton setSelected:NO];

   
    float temprature =[TempratureLabel.text floatValue];
    if (Farehnheit) {
        float celcious = (5.0/9.0) * (temprature-32);
        [TempratureLabel setText:[NSString stringWithFormat:@"%.1f °C",celcious]];
    }
    Farehnheit= false;
   
}
-(IBAction)PencilPressed:(id)sender
{
    [puckNameTextField becomeFirstResponder];
}
-(IBAction)SideMenuButtonPressed:(id)sender
{
    [self ShowMenu];
}
#pragma mark -
#pragma mark UITextField Delegate
-(BOOL)textFieldShouldBeginEditing:(UITextField *)textField
{
    isEditing = true;
    return YES;
}
-(BOOL)textFieldShouldReturn:(UITextField *)textField
{
    isEditing = false;
    NSString *str = [textField.text stringByReplacingOccurrencesOfString:@" " withString:@""];
    if (![str isEqualToString:@""]) {
        NSUserDefaults *Defaults = [NSUserDefaults standardUserDefaults];
        [Defaults setValue:textField.text forKey:appdelegate.SelectedPuck.Address];
        [Defaults synchronize];
        appdelegate.SelectedPuck.Name = textField.text;
    }
    if ([str isEqualToString:@""]) {
        NSUserDefaults *Defaults = [NSUserDefaults standardUserDefaults];
        [Defaults setValue:appdelegate.SelectedPuck.Address forKey:appdelegate.SelectedPuck.Address];
        [Defaults synchronize];
        appdelegate.SelectedPuck.Name = appdelegate.SelectedPuck.Address;
        textField.text = appdelegate.SelectedPuck.Name;
    }
    
    [ListTableView reloadData];

    return [textField resignFirstResponder];
}
-(BOOL)textFieldShouldEndEditing:(UITextField *)textField
{
    isEditing = false;
    NSString *str = [textField.text stringByReplacingOccurrencesOfString:@" " withString:@""];
    if (![str isEqualToString:@""]) {
        NSUserDefaults *Defaults = [NSUserDefaults standardUserDefaults];
        [Defaults setValue:textField.text forKey:appdelegate.SelectedPuck.Address];
        [Defaults synchronize];

        appdelegate.SelectedPuck.Name = textField.text;
    }
    [ListTableView reloadData];
    return YES;
}
-(void)textFieldDidEndEditing:(UITextField *)textField
{
    isEditing = false;
    NSString *str = [textField.text stringByReplacingOccurrencesOfString:@" " withString:@""];
    if (![str isEqualToString:@""]) {
        NSUserDefaults *Defaults = [NSUserDefaults standardUserDefaults];
        [Defaults setValue:textField.text forKey:appdelegate.SelectedPuck.Address];
        [Defaults synchronize];
        appdelegate.SelectedPuck.Name = textField.text;
    }
    [ListTableView reloadData];

}

#pragma mark -

#pragma mark Side Menu Delegates
#pragma mark -
#pragma mark - UIBarButtonItems

- (void)setupMenuBarButtonItems {

    self.navigationItem.leftBarButtonItem =[[UIBarButtonItem alloc]
                                            initWithImage:[UIImage imageNamed:@"menu_logo.png"] style:UIBarButtonItemStylePlain
                                            target:self
                                            action:@selector(ShowMenu)];

}

-(void)ShowMenuonSwipe
{
    float Width = ListTableView.frame.size.width;
    if (Width<=0) {
        [UIView animateWithDuration:0.25 animations:^{
            ListTableView.frame =  CGRectMake(0, 0.0, self.view.frame.size.width-100, self.view.frame.size.height);
        } completion:^(BOOL finished) {
        }];
    }
}
-(void)HideMenuonSwipe
{
    float Width = ListTableView.frame.size.width;
    if (Width>0) {
        [UIView animateWithDuration:0.25 animations:^{
            ListTableView.frame =  CGRectMake(0, 0.0, 0.0, self.view.frame.size.height);
        } completion:^(BOOL finished) {
            
        }];

    }

}
-(void)ShowMenu
{
    float Width = ListTableView.frame.size.width;
    if (Width<=0) {
        [UIView animateWithDuration:0.25 animations:^{
            ListTableView.frame =  CGRectMake(0, 0.0, self.view.frame.size.width-100, self.view.frame.size.height);
        } completion:^(BOOL finished) {
            
        }];
    }
    else
    {
        [UIView animateWithDuration:0.25 animations:^{
            ListTableView.frame =  CGRectMake(0, 0.0, 0.0, self.view.frame.size.height);
        } completion:^(BOOL finished) {
        }];

    }
}

#pragma mark -
#pragma mark - UITableView DataSource
-(CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section
{
    return 60;
}
-(UIView *)tableView:(UITableView *)tableView viewForHeaderInSection:(NSInteger)section
{
    UIView *Headerview = [[UIView alloc] initWithFrame:CGRectMake(0, 0, ListTableView.frame.size.width, 60)];
    UILabel *lbl = [[UILabel alloc] initWithFrame:Headerview.frame];
    [lbl setTextColor:[UIColor whiteColor]];
    [lbl setText:@"Silicon Labs Sensor Puck"];
    [lbl setFont:[UIFont fontWithName:@"HelveticaNeue" size:18]];
    
    UILabel *lbl1 = [[UILabel alloc] initWithFrame:CGRectMake(0, Headerview.frame.size.height-1, ListTableView.frame.size.width, 1)];
    lbl1.text = @"";
    [lbl1 setBackgroundColor:[UIColor whiteColor]];
    [Headerview addSubview:lbl1];

    [Headerview addSubview:lbl];
    return Headerview;
}
-(NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    [ListTableView setHidden:NO];
    return PuckArray.count;
}
-(CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
    return 60.0;
}
-(UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    static NSString *cellIdentifier = @"cellID";
    
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:
                             cellIdentifier];
    if (cell == nil) {
        cell = [[UITableViewCell alloc]initWithStyle:
                UITableViewCellStyleDefault reuseIdentifier:cellIdentifier];
        UILabel *lbl = [[UILabel alloc] initWithFrame:CGRectMake(0, 59, ListTableView.frame.size.width, 1)];
        lbl.text = @"";
        [lbl setBackgroundColor:[UIColor whiteColor]];
        [cell addSubview:lbl];
    }
    
    cell.textLabel.textColor = [UIColor whiteColor];
    
    cell.textLabel.backgroundColor = [UIColor clearColor];
    cell.contentView.backgroundColor = [UIColor clearColor];
    cell.backgroundColor = [UIColor clearColor];
    
    Puck *puck = [PuckArray objectAtIndex:indexPath.row];
    
    cell.textLabel.text = puck.Name;
    NSString *name = [[NSUserDefaults standardUserDefaults] valueForKey:puck.Address];
    if (name==nil || [name isEqualToString:@""]) {
        name = puck.Name;
    }
    
    cell.textLabel.text = name;
    if ([appdelegate.SelectedPuck.Address isEqualToString:puck.Address]) {
        [cell.contentView setBackgroundColor:[UIColor colorWithRed:0.0/255.0 green:118.0/255.0 blue:149.0/255.0 alpha:1.0]];
        [cell.textLabel setBackgroundColor:[UIColor colorWithRed:0.0/255.0 green:118.0/255.0 blue:149.0/255.0 alpha:1.0]];
        
    }
    else
    {
        cell.textLabel.backgroundColor = [UIColor clearColor];
        cell.contentView.backgroundColor = [UIColor clearColor];
    }
    
    return cell;
}
-(void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    appdelegate.SelectedPuck = [PuckArray objectAtIndex:indexPath.row];
    [ListTableView reloadData];
    puckNameTextField.text = appdelegate.SelectedPuck.Name;
    [self ShowMenu];
}

#pragma mark - UI Layout
//--- Following method update UI based on device on which application is running
-(void)SetViewLayout
{
    int FourSideMargine=0;
    int innerMargine=0;
    
    int fourSideofBoxMargine = 0;
    
    int imageviewHeight =0;
    int spaceBWimageandLabel = 0;
    
    int TitleLabelHeight  = 0;
    
    int fontsize = 11;
    int DescFontSize = 22;
    
    int HRViewX = 0;
    int HRViewFont = 0;
    
    int FontForEditText = 25;
    
    int ToplabelWidth = 241;
    int ToplabelFontSize = 19;

    if (IS_IPHONE_4_OR_LESS) {
        FourSideMargine = 12;
        innerMargine = 9;
        fourSideofBoxMargine = 10;
        imageviewHeight = 30;
        spaceBWimageandLabel = 5;
        TitleLabelHeight = 25;
        fontsize = 13;
        DescFontSize = 25;
        HRViewX = 5;
        HRViewFont = 18;
        FontForEditText = 20;
        
        ToplabelWidth = 219;
        ToplabelFontSize = 17;
        
    }
    
    if (IS_IPHONE_5) {
        FourSideMargine = 14;
        innerMargine = 11;
        fourSideofBoxMargine = 11;
        imageviewHeight = 32;
        spaceBWimageandLabel = 18;
        TitleLabelHeight = 25;
        fontsize = 14;
        DescFontSize = 32;
        
        HRViewX = 0;
        HRViewFont = 22;
        
        FontForEditText = 26;
        
        ToplabelWidth = 219;
        ToplabelFontSize = 17;
    }
    if (IS_IPHONE_6) {
        FourSideMargine = 17;
        innerMargine = 13;
        fourSideofBoxMargine = 13;
        imageviewHeight = 38;
        spaceBWimageandLabel = 25;
        TitleLabelHeight = 27;
        fontsize = 16;
        DescFontSize = 38;
        
        HRViewX = 0;
        HRViewFont = 22;
        
        FontForEditText = 31;
        
        ToplabelWidth = 266;
        ToplabelFontSize = 20;
    }
    
    if (IS_IPHONE_6P) {
        FourSideMargine = 19;
        innerMargine = 14;
        fourSideofBoxMargine = 14;
        imageviewHeight = 42;
        spaceBWimageandLabel = 28;
        TitleLabelHeight = 30;
        fontsize = 18;
        DescFontSize = 45;
        
        HRViewX = 1;
        HRViewFont = 26;
        
        FontForEditText = 32;
        
        ToplabelWidth = 299;
        ToplabelFontSize = 22;
    }
    if (IS_IPAD) {
        FourSideMargine = 26;
        innerMargine = 19;
        fourSideofBoxMargine = 19;
        imageviewHeight = 58;
        spaceBWimageandLabel = 42;
        TitleLabelHeight = 32;
        fontsize = 30;
        DescFontSize = 70;
        
        HRViewX = 5;
        HRViewFont = 34;
        
        FontForEditText = 32;
        
        ToplabelWidth = 650;
        ToplabelFontSize = 27;

    }
    UILabel *TopLabel = (UILabel *) [self.view viewWithTag:51];
    [TopLabel setFrame:CGRectMake(TopLabel.frame.origin.x, TopLabel.frame.origin.y, ToplabelWidth, TopLabel.frame.size.height)];
    [TopLabel setFont:[UIFont fontWithName:@"HelveticaNeue" size:ToplabelFontSize]];
    
    int screenheight = [UIScreen mainScreen].bounds.size.height - scrollview.frame.origin.y;
    int boxsize = (screenheight-(FourSideMargine*2)-(innerMargine*2))/3;
    int boxwidth = (scrollview.frame.size.width-(FourSideMargine*2)-innerMargine)/2;
    NSLog(@"height %f",[UIScreen mainScreen].bounds.size.height);
    int BoxTag = 101;
    for (int i=0; i<3; i++) {
        for (int j=0; j<2; j++) {
            UIView *boxview = [EnvDetailView viewWithTag:BoxTag];
            UIImageView *imgview = (UIImageView *)[boxview viewWithTag:BoxTag+100];
            UILabel *TitleLabel = (UILabel *) [boxview viewWithTag:BoxTag+200];
            UILabel *DescLabel = (UILabel *) [boxview viewWithTag:BoxTag+300];
            
            int xpos = boxwidth*j+FourSideMargine;
            int ypos = (boxsize*i)+FourSideMargine;
            if (j==1) {
                xpos+=innerMargine;
            }
            if (i>0) {
                ypos+=(innerMargine*i);
            }
            [boxview setFrame:CGRectMake(xpos, ypos, boxwidth, boxsize)];
            [boxview setBackgroundColor:[UIColor colorWithRed:77.0/255.0 green:77.0/255.0 blue:77.0/255.0 alpha:1.0]];
            [imgview setFrame:CGRectMake(fourSideofBoxMargine, fourSideofBoxMargine, boxview.frame.size.width-(fourSideofBoxMargine*2), imageviewHeight)];
            [TitleLabel setFrame:CGRectMake(fourSideofBoxMargine, imageviewHeight+fourSideofBoxMargine+spaceBWimageandLabel, boxview.frame.size.width - (fourSideofBoxMargine*2), TitleLabelHeight)];
            UIFont *font = [UIFont fontWithName:@"HelveticaNeue" size:fontsize]; //HelveticaNeue
            
            [DescLabel setFrame:CGRectMake(fourSideofBoxMargine, imageviewHeight+fourSideofBoxMargine+spaceBWimageandLabel+TitleLabel.frame.size.height, TitleLabel.frame.size.width, boxsize-(imageviewHeight+fourSideofBoxMargine+spaceBWimageandLabel+TitleLabel.frame.size.height+fourSideofBoxMargine))];
            UIFont *descfont = [UIFont fontWithName:@"HelveticaNeue" size:DescFontSize]; //HelveticaNeue
            
            [TitleLabel setFont:font];
            [DescLabel setFont:descfont];
            
            [puckNameTextField setFont:[UIFont fontWithName:@"HelveticaNeue" size:FontForEditText]];
            
            if (BoxTag==101) {
                UIButton *fbutton = (UIButton *) [boxview viewWithTag:501];
                [fbutton setFrame:CGRectMake(boxview.frame.size.width-innerMargine-(imageviewHeight*2)-3, innerMargine, imageviewHeight, imageviewHeight)];
                
                UIButton *cbutton = (UIButton *) [boxview viewWithTag:502];
                [cbutton setFrame:CGRectMake(boxview.frame.size.width-innerMargine-imageviewHeight, innerMargine, imageviewHeight, imageviewHeight)];

            }
            BoxTag++;
        }
    }
    [BiometricView setFrame:scrollview.bounds];
    [BiometricGrayView setFrame:CGRectMake(FourSideMargine, FourSideMargine, scrollview.frame.size.width-(FourSideMargine*2), scrollview.frame.size.height-(FourSideMargine*2))];
        
    
    
    if (IS_IPAD) {
        UIImageView *HeartImg = (UIImageView *) [BiometricView viewWithTag:21];
        UILabel *lbl = (UILabel *)[BiometricView viewWithTag:22];
       
        [HeartImg setFrame:CGRectMake(FourSideMargine, HeartImg.frame.origin.y, HeartImg.frame.size.width, HeartImg.frame.size.height)];
        [lbl setFrame:CGRectMake(FourSideMargine, HeartImg.frame.origin.y+HeartImg.frame.size.height+10, lbl.frame.size.width, lbl.frame.size.height)];
        [lbl setFont:[UIFont fontWithName:@"HelveticaNeue" size:22]];
        
        UILabel *HRLbl = (UILabel *)[BiometricView viewWithTag:23];
        [HRLbl setFrame:CGRectMake(FourSideMargine, lbl.frame.origin.y+lbl.frame.size.height-50, HRLbl.frame.size.width, HRLbl.frame.size.height)];
        [HRLbl setFont:[UIFont fontWithName:@"HelveticaNeue" size:55]];
        
        UIView *Graphview = [BiometricView viewWithTag:24];
         [Graphview setFrame:CGRectMake(FourSideMargine, HRLbl.frame.origin.y+HRLbl.frame.size.height, Graphview.frame.size.width-(FourSideMargine*2),BiometricView.frame.size.height-(HRLbl.frame.size.height+HRLbl.frame.origin.y+50))];
        
    }
    AmbientLightLabel.adjustsFontSizeToFitWidth = true;
    AmbientLightLabel.minimumScaleFactor = 0.5;
    HeartRateLabel.adjustsFontSizeToFitWidth = true;
    HeartRateLabel.minimumScaleFactor = 0.5;
    HRMLabel.font = TempratureLabel.font;
}


@end
