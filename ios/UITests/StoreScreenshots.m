// Captures the raw App Store screenshots in tools/store/ios-raw/ (framed by tools/store_ios_screens.py).
// Scenes 1–5 use the REAL TeleHex keyboard extension (the --embedded-preview keyboard is shorter than what iOS gives
// an extension, so its hex grid renders smaller). Skipped unless SHOTS_DIR is set. Per Simulator (iPhone 17 Pro Max,
// iPad Pro 13-inch), with Connect Hardware Keyboard off:
//   xcrun simctl status_bar <udid> override --time 9:41 --batteryState discharging --batteryLevel 100 --cellularBars 4 --wifiBars 3
//   xcodebuild ... test -only-testing:TeleHexUITests/KeyboardUITests/testEnableKeyboardInSettings CODE_SIGNING_ALLOWED=NO
//   TEST_RUNNER_SHOTS_DIR=$PWD/tools/store/ios-raw xcodebuild -project ios/TeleHex.xcodeproj -scheme TeleHex \
//     -destination 'platform=iOS Simulator,id=<udid>' -derivedDataPath out/ios-shots -parallel-testing-enabled NO \
//     test -only-testing:TeleHexUITests/StoreScreenshots CODE_SIGNING_ALLOWED=NO
// The glide uses XCTest's private event synthesis: public XCUICoordinate drags are straight lines only.
#import <XCTest/XCTest.h>

@protocol TXPointerPath <NSObject>
- (instancetype)initForTouchAtPoint:(CGPoint)point offset:(double)offset;
- (void)moveToPoint:(CGPoint)point atOffset:(double)offset;
- (void)liftUpAtOffset:(double)offset;
@end
@protocol TXEventRecord <NSObject>
- (instancetype)initWithName:(NSString *)name interfaceOrientation:(UIInterfaceOrientation)orientation;
- (void)addPointerEventPath:(id)path;
@end
@protocol TXSynthesizer <NSObject>
- (id)synthesizeEvent:(id)event completion:(void (^)(BOOL, NSError *))completion;
@end

@interface StoreScreenshots : XCTestCase
@end

@implementation StoreScreenshots

- (BOOL)setUpWithError:(NSError **)error {
    self.continueAfterFailure = NO;
    XCTSkipIf(NSProcessInfo.processInfo.environment[@"SHOTS_DIR"] == nil, @"set TEST_RUNNER_SHOTS_DIR to capture store screenshots");
    return YES;
}

- (XCUIApplication *)launch:(NSArray<NSString *> *)extra {
    XCUIApplication *app = [XCUIApplication new];
    app.launchArguments = [@[@"--reset-test-data"] arrayByAddingObjectsFromArray:extra];
    [app launch];
    return app;
}

/// Opens the app's test field with the system TeleHex keyboard (switching with the globe key) in Vietnamese mode.
- (XCUIApplication *)keyboard {
    XCUIApplication *app = [self launch:@[]];
    XCUIElement *field = app.textViews[@"typing-field"];
    XCTAssertTrue([field waitForExistenceWithTimeout:15]);
    [field tap];
    XCUIApplication *kb = [[XCUIApplication alloc] initWithBundleIdentifier:@"com.telehex.keyboard.extension"];
    for (int i = 0; i < 6 && ![kb.buttons[@"key-t"] waitForExistenceWithTimeout:4]; i++) [app.buttons[@"Next keyboard"] tap];
    XCTAssertTrue(kb.buttons[@"key-t"].exists, @"TeleHex keyboard not active; run testEnableKeyboardInSettings first");
    if ([kb.buttons[@"language-mode"].label isEqualToString:@"EN"]) [kb.buttons[@"language-mode"] tap];
    sleep(1);
    CGRect q = kb.buttons[@"key-q"].frame, space = kb.buttons[@"key- "].frame;
    NSLog(@"STORESHOT window=%@ q=%@ space=%@ screen=%@", NSStringFromCGRect(kb.windows.firstMatch.frame), NSStringFromCGRect(q),
          NSStringFromCGRect(space), NSStringFromCGRect(app.frame));
    return kb;
}

- (void)save:(NSString *)name {
    sleep(1);
    NSString *dir = NSProcessInfo.processInfo.environment[@"SHOTS_DIR"];
    XCTAssertNotNil(dir);
    NSString *idiom = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad ? @"ipad" : @"iphone";
    NSData *png = XCUIScreen.mainScreen.screenshot.PNGRepresentation;
    NSString *path = [dir stringByAppendingPathComponent:[NSString stringWithFormat:@"%@-%@.png", idiom, name]];
    XCTAssertTrue([png writeToFile:path atomically:YES], @"write %@", path);
}

- (void)type:(NSString *)keys in:(XCUIApplication *)app {  // app = keyboard extension
    for (NSUInteger i = 0; i < keys.length; i++) {
        NSString *key = [keys substringWithRange:NSMakeRange(i, 1)];
        XCUIElement *button = app.buttons[[@"key-" stringByAppendingString:key]];
        XCTAssertTrue([button waitForExistenceWithTimeout:5], @"key %@", key);
        [button tap];
    }
}

/// Swipe up on a letter = capital.
- (void)capital:(NSString *)key in:(XCUIApplication *)app {
    XCUIElement *button = app.buttons[[@"key-" stringByAppendingString:key]];
    XCUICoordinate *from = [button coordinateWithNormalizedOffset:CGVectorMake(0.5, 0.5)];
    XCUICoordinate *to = [from coordinateWithOffset:CGVectorMake(0, -45)];
    [from pressForDuration:0.05 thenDragToCoordinate:to];
}

- (CGPoint)center:(NSString *)key in:(XCUIApplication *)app {
    CGRect f = app.buttons[[@"key-" stringByAppendingString:key]].frame;
    return CGPointMake(CGRectGetMidX(f), CGRectGetMidY(f));
}

/// Multi-point glide through the given keys (private XCTest event synthesis).
- (void)glide:(NSArray<NSString *> *)keys in:(XCUIApplication *)app {
    id path = [(id<TXPointerPath>)[NSClassFromString(@"XCPointerEventPath") alloc] initForTouchAtPoint:[self center:keys[0] in:app] offset:0];
    double t = 0;
    for (NSUInteger i = 1; i < keys.count; i++) {
        CGPoint a = [self center:keys[i - 1] in:app], b = [self center:keys[i] in:app];
        for (int s = 1; s <= 8; s++) {
            t += 0.025;
            [path moveToPoint:CGPointMake(a.x + (b.x - a.x) * s / 8, a.y + (b.y - a.y) * s / 8) atOffset:t];
        }
    }
    [path liftUpAtOffset:t + 0.05];
    id record = [(id<TXEventRecord>)[NSClassFromString(@"XCSynthesizedEventRecord") alloc] initWithName:@"glide" interfaceOrientation:UIInterfaceOrientationPortrait];
    [record addPointerEventPath:path];
    id<TXSynthesizer> synth = [XCUIDevice.sharedDevice valueForKey:@"eventSynthesizer"];
    XCTestExpectation *done = [self expectationWithDescription:@"glide"];
    [synth synthesizeEvent:record completion:^(BOOL ok, NSError *error) { XCTAssertTrue(ok, @"%@", error); [done fulfill]; }];
    [self waitForExpectations:@[done] timeout:10];
}

- (void)test1Hero {
    XCUIApplication *kb = [self keyboard];
    [self capital:@"t" in:kb];
    [self type:@"oois nay ddi awn phowr khoong " in:kb];
    [self save:@"1-hero"];
}

- (void)test2Telex {
    XCUIApplication *kb = [self keyboard];
    [self capital:@"d" in:kb];
    [self type:@"di awn" in:kb];
    [self save:@"2-telex"];
}

- (void)test3Glide {
    XCUIApplication *kb = [self keyboard];
    [self capital:@"d" in:kb];
    [self type:@"di awn " in:kb];
    [self glide:@[@"p", @"h", @"o"] in:kb];
    [self save:@"3-glide"];
}

- (void)test4English {
    XCUIApplication *kb = [self keyboard];
    [kb.buttons[@"language-mode"] tap];
    [self capital:@"h" in:kb];
    [self type:@"ell" in:kb];
    [self save:@"4-english"];
    [kb.buttons[@"language-mode"] tap];
}

/// Switches the keyboard to the built-in QWERTY profile from its ⚙ panel, captures, then switches back to Tổ ong.
- (void)profile:(NSString *)identifier in:(XCUIApplication *)kb {
    [kb.buttons[@"key-#settings"] tap];
    XCUIElement *row = kb.buttons[[@"profile-" stringByAppendingString:identifier]];
    XCTAssertTrue([row waitForExistenceWithTimeout:10]);
    XCUIElement *panel = kb.scrollViews[@"keyboard-panel"];
    for (int i = 0; i < 12 && !row.isHittable; i++) [panel swipeUp];
    [row tap];
    [kb.buttons[@"close-settings"] tap];
}

- (void)test5Traditional {
    XCUIApplication *kb = [self keyboard];
    [self profile:@"builtin.qwerty" in:kb];
    XCTAssertTrue([kb.buttons[@"key-#shift"] waitForExistenceWithTimeout:10]);
    [kb.buttons[@"key-#shift"] tap];
    [self type:@"xin chaof " in:kb];
    [self save:@"5-traditional"];
    [self profile:@"builtin.default" in:kb];
}

- (void)test6Privacy {
    XCUIApplication *app = [self launch:@[]];
    XCUIElement *row = app.buttons[@"settings-data"];
    XCTAssertTrue([row waitForExistenceWithTimeout:15]);
    for (int i = 0; i < 8 && !row.isHittable; i++) [app swipeUp];
    [row tap];
    XCTAssertTrue([app.switches[@"incognito"] waitForExistenceWithTimeout:10]);
    [app.switches[@"incognito"] tap];
    [self save:@"6-privacy"];
}

@end
