/*
 * Copyright (C) 2013-2015 RoboVM AB
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * 
 * Portions of this code is based on Apple Inc's LocateMe sample (v4.0)
 * which is copyright (C) 2008-2014 Apple Inc.
 */

package org.robovm.samples.locateme.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.robovm.apple.coregraphics.CGPoint;
import org.robovm.apple.coregraphics.CGRect;
import org.robovm.apple.corelocation.CLErrorCode;
import org.robovm.apple.corelocation.CLLocation;
import org.robovm.apple.corelocation.CLLocationManager;
import org.robovm.apple.corelocation.CLLocationManagerDelegateAdapter;
import org.robovm.apple.foundation.Foundation;
import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSDateFormatter;
import org.robovm.apple.foundation.NSDateFormatterStyle;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.foundation.NSIndexPath;
import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.uikit.UIActivityIndicatorView;
import org.robovm.apple.uikit.UIActivityIndicatorViewStyle;
import org.robovm.apple.uikit.UIBarButtonItem;
import org.robovm.apple.uikit.UIBarButtonItemStyle;
import org.robovm.apple.uikit.UIButton;
import org.robovm.apple.uikit.UILabel;
import org.robovm.apple.uikit.UINavigationController;
import org.robovm.apple.uikit.UIStoryboardSegue;
import org.robovm.apple.uikit.UITableView;
import org.robovm.apple.uikit.UITableViewCell;
import org.robovm.apple.uikit.UITableViewCellAccessoryType;
import org.robovm.apple.uikit.UITableViewCellSelectionStyle;
import org.robovm.apple.uikit.UITableViewCellStyle;
import org.robovm.apple.uikit.UITableViewDataSourceAdapter;
import org.robovm.apple.uikit.UITableViewDelegateAdapter;
import org.robovm.apple.uikit.UITableViewStyle;
import org.robovm.apple.uikit.UIView;
import org.robovm.apple.uikit.UIViewAutoresizing;
import org.robovm.apple.uikit.UIViewController;
import org.robovm.objc.annotation.CustomClass;
import org.robovm.objc.annotation.IBOutlet;
import org.robovm.objc.block.VoidBooleanBlock;
import org.robovm.samples.locateme.util.Str;

@CustomClass("TrackLocationViewController")
public class TrackLocationViewController extends UIViewController implements SetupViewControllerDelegate {
    private SetupViewController setupViewController;

    @IBOutlet
    private UIButton startButton;
    @IBOutlet
    private UILabel descriptionLabel;
    @IBOutlet
    private UITableView tableView;

    private CLLocationManager locationManager;
    private List<CLLocation> locationMeasurements;
    private NSDateFormatter dateFormatter;
    private String stateString;

    @Override
    public void viewDidLoad() {
        super.viewDidLoad();
        locationMeasurements = new ArrayList<>();

        dateFormatter = new NSDateFormatter();
        dateFormatter.setDateStyle(NSDateFormatterStyle.Medium);
        dateFormatter.setTimeStyle(NSDateFormatterStyle.Long);

        tableView.setDataSource(new UITableViewDataSourceAdapter() {
            /*
             * The table view has two sections. The first has 1 row which
             * displays status information. The second has a row for each valid
             * location object received from the location manager.
             */
            @Override
            public long getNumberOfSections(UITableView tableView) {
                return (locationMeasurements.size() > 0) ? 2 : 1;
            }

            @Override
            public String getTitleForHeader(UITableView tableView, long section) {
                switch ((int) section) {
                case 0:
                    return Str.getLocalizedString("Status");
                default:
                    return Str.getLocalizedString("All Measurements");
                }
            }

            @Override
            public long getNumberOfRowsInSection(UITableView tableView, long section) {
                switch ((int) section) {
                case 0:
                    return 1;
                default:
                    return locationMeasurements.size();
                }
            }

            @Override
            public UITableViewCell getCellForRow(UITableView tableView, NSIndexPath indexPath) {
                UITableViewCell cell;
                switch (indexPath.getSection()) {
                case 0:
                    /*
                     * The cell for the status row uses the cell style
                     * "UITableViewCellStyleValue1", which has a label on the
                     * left side of the cell with left-aligned and black text;
                     * on the right side is a label that has smaller blue text
                     * and is right-aligned. An activity indicator has been
                     * added to the cell and is animated while the location
                     * manager is updating. The cell's text label displays the
                     * current state of the manager.
                     */
                    final String StatusCellID = "StatusCellID";
                    final int StatusCellActivityIndicatorTag = 2;
                    UIActivityIndicatorView activityIndicator = null;
                    cell = tableView.dequeueReusableCell(StatusCellID);
                    if (cell == null) {
                        cell = new UITableViewCell(UITableViewCellStyle.Value1, StatusCellID);
                        cell.setSelectionStyle(UITableViewCellSelectionStyle.None);
                        activityIndicator = new UIActivityIndicatorView(UIActivityIndicatorViewStyle.Gray);
                        CGRect frame = activityIndicator.getFrame();
                        frame.setOrigin(new CGPoint(290, 12));
                        activityIndicator.setFrame(frame);
                        activityIndicator.setAutoresizingMask(UIViewAutoresizing.FlexibleLeftMargin);
                        activityIndicator.setTag(StatusCellActivityIndicatorTag);
                        cell.getContentView().addSubview(activityIndicator);
                    } else {
                        activityIndicator = (UIActivityIndicatorView) cell.getContentView().getViewWithTag(
                                StatusCellActivityIndicatorTag);
                    }

                    cell.getTextLabel().setText(stateString);
                    if (stateString != null && stateString.equals(Str.getLocalizedString("Tracking"))) {
                        if (!activityIndicator.isAnimating())
                            activityIndicator.startAnimating();
                    } else {
                        if (activityIndicator.isAnimating())
                            activityIndicator.stopAnimating();
                    }
                    break;
                default:
                    /*
                     * The cells for the location rows use the cell style
                     * "UITableViewCellStyleSubtitle", which has a left-aligned
                     * label across the top and a left-aligned label below it in
                     * smaller gray text. The text label shows the coordinates
                     * for the location and the detail text label shows its
                     * timestamp.
                     */
                    final String OtherMeasurementsCellID = "OtherMeasurementsCellID";
                    cell = tableView.dequeueReusableCell(OtherMeasurementsCellID);
                    if (cell == null) {
                        cell = new UITableViewCell(UITableViewCellStyle.Subtitle, OtherMeasurementsCellID);
                        cell.setAccessoryType(UITableViewCellAccessoryType.DisclosureIndicator);
                    }
                    CLLocation location = locationMeasurements.get(indexPath.getRow());
                    cell.getTextLabel().setText(Str.getLocalizedCoordinateString(location));
                    cell.getDetailTextLabel().setText(dateFormatter.format(location.getTimestamp()));
                    break;
                }
                return cell;
            }
        });
        tableView.setDelegate(new UITableViewDelegateAdapter() {
            /**
             * Delegate method invoked before the user selects a row. In this
             * sample, we use it to prevent selection in the first section of
             * the table view.
             */
            @Override
            public NSIndexPath willSelectRow(UITableView tableView, NSIndexPath indexPath) {
                return (indexPath.getSection() == 0) ? null : indexPath;
            }

            /**
             * Delegate method invoked after the user selects a row. Selecting a
             * row containing a location object will navigate to a new view
             * controller displaying details about that location.
             */
            @Override
            public void didSelectRow(UITableView tableView, NSIndexPath indexPath) {
                tableView.deselectRow(indexPath, true);
                CLLocation location = locationMeasurements.get(indexPath.getRow());

                LocationDetailViewController locationDetailViewController = new LocationDetailViewController(
                        UITableViewStyle.Grouped);
                locationDetailViewController.setLocation(location);
                getNavigationController().pushViewController(locationDetailViewController, true);
            }
        });
    }

    @Override
    public void prepareForSegue(UIStoryboardSegue segue, NSObject sender) {
        UINavigationController nv = (UINavigationController) segue.getDestinationViewController();
        setupViewController = (SetupViewController) nv.getViewControllers().first();
        setupViewController.configure(true);
        setupViewController.setDelegate(this);
    }

    /**
     * The reset method allows the user to repeatedly test the location
     * functionality. In addition to discarding all of the location measurements
     * from the previous "run", it animates a transition in the user interface
     * between the table which displays location data and the start button and
     * description label presented at launch.
     */
    private void reset() {
        locationMeasurements.clear();

        // fade in the rest of the UI and fade out the table view
        UIView.animate(0.6, new Runnable() {
            @Override
            public void run() {
                startButton.setAlpha(1);
                descriptionLabel.setAlpha(1);
                tableView.setAlpha(0);
                getNavigationItem().setLeftBarButtonItem(null, true);
            }
        }, new VoidBooleanBlock() {
            @Override
            public void invoke(boolean finished) {
                if (finished) {
                    // ..
                }
            }
        });
    }

    private void stopUpdatingLocation(String state) {
        stateString = state;
        tableView.reloadData();

        locationManager.stopUpdatingLocation();
        locationManager.setDelegate(null);
    }

    /**
     * This method is invoked when the user hits "Done" in the setup view
     * controller. The options chosen by the user are passed in as a map. The
     * keys for this map are declared in SetupViewController.
     */
    @Override
    public void didFinishSetup(SetupViewController viewController, Map<String, Double> setupInfo) {
        startButton.setAlpha(0);
        descriptionLabel.setAlpha(0);
        tableView.setAlpha(1);

        // Create the manager object
        locationManager = new CLLocationManager();
        locationManager.setDelegate(new CLLocationManagerDelegateAdapter() {
            /**
             * We want to get and store a location measurement that meets the
             * desired accuracy. For this example, we are going to use
             * horizontal accuracy as the deciding factor. In other cases, you
             * may wish to use vertical accuracy, or both together.
             */
            @Override
            public void didUpdateLocations(CLLocationManager manager, NSArray<CLLocation> locations) {
                CLLocation newLocation = locations.last();

                // test that the horizontal accuracy does not indicate an
                // invalid measurement
                if (newLocation.getHorizontalAccuracy() < 0)
                    return;
                // test the age of the location measurement to determine if the
                // measurement is cached
                // in most cases you will not want to rely on cached
                // measurements
                double locationAge = -newLocation.getTimestamp().getTimeIntervalSinceNow();
                if (locationAge > 5.0)
                    return;
                // store all of the measurements, just so we can see what kind
                // of data we might receive
                locationMeasurements.add(newLocation);
                // update the display with the new location data
                tableView.reloadData();
            }

            @Override
            public void didFail(CLLocationManager manager, NSError error) {
                // The location "unknown" error simply means the manager is
                // currently unable to get the location.
                if (error.getErrorCode() != CLErrorCode.LocationUnknown) {
                    stopUpdatingLocation(Str.getLocalizedString("Error"));
                }
            }
        });

        // This is the most important property to set for the manager. It
        // ultimately determines how the manager will
        // attempt to acquire location and thus, the amount of power that will
        // be consumed.
        locationManager.setDesiredAccuracy(setupInfo.get(SetupViewController.SETUP_INFO_KEY_ACCURACY));
        // When "tracking" the user, the distance filter can be used to control
        // the frequency with which location measurements
        // are delivered by the manager. If the change in distance is less than
        // the filter, a location will not be delivered.
        locationManager.setDistanceFilter(setupInfo.get(SetupViewController.SETUP_INFO_KEY_DISTANCE_FILTER));

        // Once configured, the location manager must be "started".
        //
        // for iOS 8, specific user level permission is required,
        // "when-in-use" authorization grants access to the user's location
        //
        // important: be sure to include NSLocationWhenInUseUsageDescription
        // along with its
        // explanation string in your Info.plist or startUpdatingLocation will
        // not work.
        if (Foundation.getMajorSystemVersion() >= 8) {
            locationManager.requestWhenInUseAuthorization();
        }
        locationManager.startUpdatingLocation();

        stateString = Str.getLocalizedString("Tracking");
        tableView.reloadData();

        UIBarButtonItem resetItem = new UIBarButtonItem(Str.getLocalizedString("Reset"), UIBarButtonItemStyle.Plain,
                new UIBarButtonItem.OnClickListener() {
                    @Override
                    public void onClick(UIBarButtonItem barButtonItem) {
                        reset();
                    }
                });
        getNavigationItem().setLeftBarButtonItem(resetItem, true);
    }
}
