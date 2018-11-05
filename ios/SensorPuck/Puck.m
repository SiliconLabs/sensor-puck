//
//  Puck.m
//  BlueCenter
//
//  Created by Lets nurture on 30/10/14.
//  Copyright (c) 2014 Lets nurture. All rights reserved.
//

#import "Puck.h"

@implementation Puck

@synthesize Address;
@synthesize Name;
@synthesize MeasurementMode;
@synthesize Sequence,Humidity;
@synthesize Temperature;
@synthesize AmbientLight;
@synthesize UV_Index;
@synthesize Battery;
@synthesize HRM_State;
@synthesize HRM_Rate;
@synthesize HRM_Sample;
@synthesize PrevCount;
@synthesize PrevSequence;
@synthesize RecvCount;
@synthesize UniqueCount;
@synthesize LostCount;
@synthesize IdleCount;
@synthesize lastDiscoveryTime;
-(id)init
{
    
    return [super init];
}
@end
