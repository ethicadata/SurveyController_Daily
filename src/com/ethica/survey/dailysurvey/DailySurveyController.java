/*
 * Copyright (c) 2016 Ethica Data Services, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so.
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.ethica.survey.dailysurvey;

import java.text.SimpleDateFormat;
import java.util.Locale;
import android.content.Context;
import java.util.Calendar;
import java.util.Random;

import com.ethica.logger.api.data.DataContract;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * <p>This controller divides a given day into certain number of blocks, and triggers
 * the survey at a random time in each of those blocks.</p>
 * <p>The blocks don't necessarily have to cover 24 hours a day. The code specifies
 * the start time and end time of the day for each day. Only the duration between
 * the specified start/end time will be divided into the blocks of equal duration</p>
 *
 * @author Mohammad Hashemian (mohammad@ethicadata.ca)
 */
public class DailySurveyController {

    private static final String TAG = "ETHICA:DSC";
    private static final int NO_SURVEY = -1;

    /**
     * The number of blocks per day. A survey will be triggered in each block.
     */
    private static final int TIME_BLOCKS = 3;
    /**
     * The start hour of the day.
     */
    private static final int START_HOUR = 8;
    /**
     * The end hour of the day.
     */
    private static final int END_HOUR = 20;
    /**
     * The minimum interval between two prompts. Two consecutive prompts will
     * not be closer than this interval.
     */
    private static final int MIN_PROMPT_INTERVAL_MS = 2 * 60 * 60 * 1000;

    private static SimpleDateFormat mSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    /**
     * The controller randomly generates the time for each prompt, and stores
     * them here. These values will be set to 0 if they are already prompted.
     * The values in this array will be sorted chronologically, so the earliest
     * prompt time will appear first.
     */
    private long[] mTriggerTimes = null;
    /**
     * The version of Ethica app. This will be used to send reports to server on
     * app's operation.
     */
    private int mVersionCode;
    /**
     * The Android context under which this code will be executed. It can be used
     * to interact with different Android feature while running on the phone.
     */
    private Context mContext;

    /**
     * This will be invoked by Ethica app as soon as the context analyzer is
     * loaded, and prior to any other call to the functions here.
     * It allows the context analyzer to perform any initialization tasks required.
     */
    public void init(final Context c) {
        mContext = c;
        try {
            // Fetching the version code of Ethica app.
            mVersionCode = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0).versionCode;
        } catch (final NameNotFoundException e) {
            // If failed, forget it.
        }
        // Initialize the prompt times.
        mTriggerTimes = initTriggerTimes();
    }

    /**
     * <p>This function will be invoked peiodically, once approximately every 5 minutes,
     * and is responsible to dtermine whether a questionnaire from the current
     * survey should be issued or not</p>
     *
     * <p>In this context analyzer, we want to issue a survey at the specified times
     * which are already generated and stored in {@code #mTriggerTimes}. So if the
     * current time is passed the minimum non-zero time in {@code #mTriggerTimes},
     * we will trigger the survey and set that prompt time to 0, indicating that
     * it's already been used.</p>
     *
     * @return The ID of the questionnaire to be issued. This ID should be defined
     *         in the associated survey content JSON file. Returns -1 to skip
     *         issuing the survey.
     */
    public Object[] shouldShow() {
        // This will store the report on current operation, to be sent to the server.
        StringBuilder debugMsgBuilder = new StringBuilder();
        long now = System.currentTimeMillis();

        if (mTriggerTimes == null) {
            // If the trigger times are not initialized, do so
            debugMsgBuilder.append("Uninitialized trigger time. ");
            mTriggerTimes = initTriggerTimes();
        }

        for (int i = 0;i < mTriggerTimes.length;i++) {
            if (mTriggerTimes[i] == 0) {
                // If zero, it's already been used.
                continue;
            } else if (mTriggerTimes[i] > now) {
                // If the current time is already in future, we don't have to
                // issue any survey at the moment. Just send the report.
                debugMsgBuilder.append("Not the time to prompt.");
                sendReport(debugMsgBuilder.toString());
                return new Object[] {NO_SURVEY};
            } else if (mTriggerTimes[i] < now) {
                // If the current time is not zero and is in the past, issue
                // a survey for it, and send the report. Also set the time to zero,
                // so it will not be used again.
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(mTriggerTimes[i]);
                debugMsgBuilder.append("Prompting now for ")
                                .append(mSDF.format(cal.getTime()));
                sendReport(debugMsgBuilder.toString());
                mTriggerTimes[i] = 0;
                return new Object[] {1};
            }
        }

        // If all trigger times were zero, we are done with all prompt times for
        // today, so generate new ones for tomorrow.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        mTriggerTimes = getTriggerTimes(cal.getTimeInMillis());
        addTimeToDebugMsg(debugMsgBuilder, mTriggerTimes);
        sendReport(debugMsgBuilder.toString());
        return new Object[] {NO_SURVEY};
    }

    /**
     * <p>This function initializes the trigger times, only when the context
     * analyzer is loaded for the first time. A context analyzer can be loaded
     * at any time of the day. So we start generating random prompts for the
     * current day, and check how many of them are already passed. Then we set
     * the ones already passed to 0, meaning that should not lead to a prompt.<p>
     * <p>Following that, we will check to see if there is any more prompts
     * remained for today. If no, we will generate prompts for tomorrow.</p>
     *
     * @return The list of times, either for today or tomorrow, when the prompts
     *          should be triggered, sorted ascendingly. At least one of the items
     *          in this list is non-zero.
     */
    private long[] initTriggerTimes() {
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();

        // Get the prompt times for today
        long[] vals = getTriggerTimes(now);
        boolean atLeastOneNonZero = false;

        // Set the ones already passed to zero
        for (int i = 0;i < vals.length;i++) {
            if (vals[i] < now) {
                vals[i] = 0;
            } else {
                atLeastOneNonZero = true;
            }
        }

        if (!atLeastOneNonZero) {
            // If all of the prompts generated for today are passed already,
            // generate prompts for tomorrow.
            cal.add(Calendar.DATE, 1);
            vals = getTriggerTimes(cal.getTimeInMillis());
        }

        // Send a report on the prompt times to the server.
        StringBuilder debugMsgBuilder = new StringBuilder();
        addTimeToDebugMsg(debugMsgBuilder, vals);
        sendReport(debugMsgBuilder.toString());
        return vals;
    }

    /**
     * <p>Generates random trigger times as specified by {@code TIME_BLOCKS}, between
     * {@code START_HOUR} and {@code END_HOUR} hour of the day, in the day
     * specified by the {@code #date} paramter.<p>
     * <p>It will also ensure the prompt times are at least {@code #MIN_PROMPT_INTERVAL_MS}
     * apart</p>
     */
    private long[] getTriggerTimes(long date) {
        Random r = new Random();
        // Create an array to store the prompt time for each time block
        long[] values = new long[TIME_BLOCKS];
        // Calculate the length of each time block in milliseconds
        int intervalInMs = ((END_HOUR - START_HOUR) / TIME_BLOCKS) * 60 * 60 * 1000;

        // Start from the lower bound of the first time block
        Calendar calLowerBound = Calendar.getInstance();
        calLowerBound.setTimeInMillis(date);
        calLowerBound.set(Calendar.HOUR_OF_DAY, START_HOUR);
        calLowerBound.set(Calendar.MINUTE, 0);
        calLowerBound.set(Calendar.SECOND, 0);
        calLowerBound.set(Calendar.MILLISECOND, 0);

        // Generate the first prompt time in the first time block.
        values[0] = calLowerBound.getTimeInMillis() + r.nextInt(intervalInMs);
        for (int i = 1;i < TIME_BLOCKS;i++) {
            // Set the lower bound to the start of the next time block
            calLowerBound.setTimeInMillis(calLowerBound.getTimeInMillis() + intervalInMs);
            do {
                // Generate prompt times in the current time block, until one time block
                // is generated which is at least MIN_PROMPT_INTERVAL_MS away
                // from previous prompt time.
                values[i] = calLowerBound.getTimeInMillis() + r.nextInt(intervalInMs);
            } while (values[i] - values[i - 1] < MIN_PROMPT_INTERVAL_MS);
        }
        return values;
    }

    /**
     * Format the prompt times into human-readable strings for upload to the server
     */
    private void addTimeToDebugMsg(StringBuilder sb, long[] times) {
        Calendar cal = Calendar.getInstance();
        sb.append("Generated trigger time: ");
        for (int i = 0;i < times.length;i++) {
            cal.setTimeInMillis(times[i]);
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(mSDF.format(cal.getTime()));
        }
    }

    /**
     * Add the given message to the {@code LogMessage} database, so it can be uploaded
     * to the server as soon as possible.
     */
    public void sendReport(final String message) {
        final ContentValues cv = new ContentValues();
        cv.put(DataContract.LogMessage.COLUMN_NAME_KEY_TIMESTAMP, System.currentTimeMillis());
        cv.put(DataContract.LogMessage.COLUMN_NAME_KEY_VERSION_CODE, mVersionCode);
        cv.put(DataContract.LogMessage.COLUMN_NAME_KEY_MESSAGE, message);
        cv.put(DataContract.LogMessage.COLUMN_NAME_KEY_TAG, TAG);
        mContext.getContentResolver().insert(DataContract.LogMessage.CONTENT_URI, cv);
        // Also print the message to the logcat for debugging purposes.
        Log.d(TAG, message);
    }
}
