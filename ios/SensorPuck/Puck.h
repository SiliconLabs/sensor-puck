//
//  Puck.h
//  BlueCenter
//
//  Created by Lets nurture on 30/10/14.
//  Copyright (c) 2014 Lets nurture. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface Puck : NSObject
@property (nonatomic,strong)NSString *Address;
@property (nonatomic,strong)NSString *Name;

/* Sensor data */
@property int    MeasurementMode;
@property int    Sequence;
@property float  Humidity;
@property float  Temperature;
@property int    AmbientLight;
@property int    UV_Index;
@property float  Battery;
@property int    HRM_State;
@property int    HRM_Rate;
@property NSUUID *identifier;
@property (nonatomic,strong) NSMutableArray *HRM_Sample;
@property int   isRemoved;
@property (nonatomic,strong) NSDate *lastDiscoveryTime;

/* Statistics */
@property int PrevSequence;
@property int RecvCount;
@property int PrevCount;
@property int UniqueCount;
@property int LostCount;
@property int IdleCount;

@end
