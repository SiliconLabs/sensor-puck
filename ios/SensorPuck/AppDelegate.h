//
//  AppDelegate.h
//  BlueCenter
//
//  Created by Lets nurture on 14/10/14.
//  Copyright (c) 2014 Lets nurture. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "Puck.h"
@interface AppDelegate : UIResponder <UIApplicationDelegate>

@property (strong, nonatomic) UIWindow *window;
@property (strong, nonatomic) NSMutableDictionary *AllSensorsData;
@property (strong, nonatomic) NSMutableDictionary *DisplySensorsData;
@property (strong,nonatomic) NSString *selecteSensorID;
@property BOOL isConnected;
@property (strong,nonatomic) NSArray *NamesArray;

@property (strong,nonatomic) Puck *SelectedPuck;

@end

