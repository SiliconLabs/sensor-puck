//
//  EnvDetailViewController.h
//  BlueCenter
//
//  Created by Lets nurture on 15/10/14.
//  Copyright (c) 2014 Lets nurture. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <JavaScriptCore/JavaScriptCore.h>

//#import <BluetoothManager/BluetoothDevice.h>
#import "PuckCellTableViewCell.h"

@interface EnvDetailViewController : UIViewController <CBCentralManagerDelegate,UITextFieldDelegate,UITableViewDataSource,UITableViewDelegate>
{
    NSUUID *selectedpuckUUID;
    BOOL isEditing;
    
    //-------- Edit Puck
    IBOutlet UIView *EditPuckContainerView;
    IBOutlet UITextField *puckNameTextField;
    IBOutlet UIButton *EditPuckButton;
    
    //-------------Environmental Detail
    IBOutlet UILabel *TempratureLabel;
    IBOutlet UILabel *HumidityLabel;
    IBOutlet UILabel *AmbientLightLabel;
    IBOutlet UILabel *UVLightLabel;
    IBOutlet UILabel *BatteryLabel;
    IBOutlet UIView *EnvDetailView;
    
    IBOutlet UIButton *FarehnheitButton;
    IBOutlet UIButton *CelsiusButton;
    
    IBOutlet UIImageView *BatteryImageView;
    IBOutlet UIImageView *UVImageView;
    
    IBOutlet UILabel *HeartRateLabel;
    //----------------------------------
    
    //-------------- Biometric Detail
    IBOutlet UIView *BiometricView;
    IBOutlet UILabel *HRMLabel;
    IBOutlet UIView *BiometricGrayView;
    //----------------------------------
    
    
    IBOutlet UIScrollView *scrollview;
    
    
    //------- data of peripheral
//    NSData *data;
    
    //----- Boolean to check Farehnheit or cel
    BOOL Farehnheit;
    
    //---- Timer to remove puck from array;
    NSTimer *timer;
    int secondsLeft;
    
    //---- TableView to list pucks
    IBOutlet UITableView *ListTableView;
    
    //--- Graph container view
    IBOutlet UIView *graphContainerView;
}
@property (nonatomic,strong)NSMutableArray *PuckArray;
@property (nonatomic, strong) CBCentralManager *manager;
-(IBAction)FarehnheitPressed:(id)sender;
-(IBAction)PencilPressed:(id)sender;
-(IBAction)SideMenuButtonPressed:(id)sender;
@end
