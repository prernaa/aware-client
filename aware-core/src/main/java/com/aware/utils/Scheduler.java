package com.aware.utils;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.providers.Scheduler_Provider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class Scheduler extends Service {

    public static final String SCHEDULE_TRIGGER = "trigger";
    public static final String SCHEDULE_ACTION = "action";
    public static final String SCHEDULE_ID = "schedule_id";

    public static final String TRIGGER_HOUR = "hour"; //done
    public static final String TRIGGER_TIMER = "timer"; //done
    public static final String TRIGGER_WEEKDAY = "weekday"; //done
    public static final String TRIGGER_MONTH = "month"; //done
    public static final String TRIGGER_CONTEXT = "context"; //done

    public static final String TRIGGER_RANDOM = "random";
    public static final String RANDOM_HOUR = "random_hour";
    public static final String RANDOM_MONTH = "random_month";
    public static final String RANDOM_WEEKDAY = "random_weekday";

    public static final String ACTION_TYPE = "type";
    public static final String ACTION_TYPE_BROADCAST = "broadcast";
    public static final String ACTION_TYPE_SERVICE = "service";
    public static final String ACTION_TYPE_ACTIVITY = "activity";

    public static final String ACTION_CLASS = "class";
    public static final String ACTION_EXTRAS = "extras";
    public static final String ACTION_EXTRA_KEY = "extra_key";
    public static final String ACTION_EXTRA_VALUE = "extra_value";

    //String is the scheduler ID
    private static final Hashtable<String, Hashtable<IntentFilter, BroadcastReceiver>> schedulerListeners = new Hashtable<>();

    @Override
    public void onCreate() {
        super.onCreate();

        removeSchedule("debug");

        try {

            Schedule schedule = new Schedule("debug");
            schedule.addWeekday("Thursday")
                    .setActionType(ACTION_TYPE_ACTIVITY)
                    .setActionClass("com.facebook.katana.activity.FbMainTabActivity");

            Log.d(Aware.TAG, schedule.build().toString(1));

            saveSchedule( schedule );

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void saveSchedule( Schedule schedule ) {
        try {
            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Scheduler_Provider.Scheduler_Data.DEVICE_ID, Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID, schedule.getScheduleID());
            data.put(Scheduler_Provider.Scheduler_Data.SCHEDULE, schedule.build().toString());

            Cursor schedules = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null, null);
            if( schedules != null && schedules.getCount() == 1 ) {
                Log.d(Aware.TAG, "Updating already existing schedule...");
                getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null );
            } else {
                Log.d(Aware.TAG, "New schedule: " + data.toString());
                getContentResolver().insert(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data);
            }
            if( schedules != null && ! schedules.isClosed() ) schedules.close();

        } catch( JSONException e ) {
            Log.e(Aware.TAG, "Error saving schedule");
        }
    }

    public void removeSchedule( String schedule_id ) {
        getContentResolver().delete(Scheduler_Provider.Scheduler_Data.CONTENT_URI, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule_id + "'", null);
    }

    /**
     * Scheduler object that contains<br/>
     * - schedule ID<br/>
     * - schedule action<br/>
     * - schedule trigger
     */
    public class Schedule {

        private JSONObject schedule = new JSONObject();
        private JSONObject trigger = new JSONObject();
        private JSONObject action = new JSONObject();

        public Schedule( String schedule_id ){
            try {
                this.schedule.put(SCHEDULE_ID, schedule_id);
                this.schedule.put(SCHEDULE_ACTION, this.action);
                this.schedule.put(SCHEDULE_TRIGGER, this.trigger);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        /**
         * Rebuild schedule object from database JSON
         * @param schedule
         * @return
         */
        public Schedule rebuild( JSONObject schedule ) {
            try {
                this.schedule = schedule.getJSONObject("schedule");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return this;
        }

        public String getScheduleID() throws JSONException {
            return this.schedule.getString(SCHEDULE_ID);
        }

        /**
         * Generates a JSONObject representation for saving JSON to database
         * @return
         * @throws JSONException
         */
        public JSONObject build() throws JSONException {
            JSONObject schedule = new JSONObject();
            schedule.put( "schedule", this.schedule );
            return schedule;
        }

        public Schedule setActionType( String type ) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_TYPE, type);
            return this;
        }

        /**
         * Get type of action
         * @return
         * @throws JSONException
         */
        public String getActionType() throws JSONException{
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getString(ACTION_TYPE);
        }

        /**
         * Get action class
         * @return
         * @throws JSONException
         */
        public String getActionClass() throws JSONException {
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getString(ACTION_CLASS);
        }

        public Schedule setActionClass( String classname ) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_CLASS, classname);
            return this;
        }

        public Schedule addHour( int hour ) throws JSONException {
            JSONArray hours = getHours();
            hours.put(hour);
            return this;
        }

        public JSONArray getActionExtras() throws JSONException {
            if( ! this.schedule.getJSONObject(SCHEDULE_ACTION).has(ACTION_EXTRAS) ) {
                this.schedule.getJSONObject(SCHEDULE_ACTION).put(ACTION_EXTRAS, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_ACTION).getJSONArray(ACTION_EXTRAS);
        }

        public Schedule addActionExtra( String key, Object value ) throws JSONException{
            JSONArray extras = getActionExtras();
            extras.put(new JSONObject().put(ACTION_EXTRA_KEY, key).put(ACTION_EXTRA_VALUE, value));
            return this;
        }

        /**
         * Get scheduled hours
         * @return
         */
        public JSONArray getHours() throws JSONException {
            if( ! this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_HOUR) ) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_HOUR, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_HOUR);
        }

        /**
         * Set trigger to a specified date and time
         * @param date
         */
        public void setTimer( Calendar date ) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_TIMER, date.getTimeInMillis());
        }

        /**
         * Get trigger specific unix timestamp
         * @return
         */
        public long getTimer() throws JSONException {
            if( this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_TIMER) ) {
                return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getLong(TRIGGER_TIMER);
            }
            return -1;
        }

        /**
         * Add a weekday e.g., "Monday",...,"Sunday"
         * @param week_day
         * @return
         * @throws JSONException
         */
        public Schedule addWeekday( String week_day ) throws JSONException {
            JSONArray weekdays = getWeekdays();
            weekdays.put(week_day);
            return this;
        }

        /**
         * Get days of week in which this trigger is scheduled
         * @return
         */
        public JSONArray getWeekdays() throws JSONException {
            if( ! this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_WEEKDAY) ) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_WEEKDAY, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_WEEKDAY);
        }

        /**
         * Add a month e.g., "January",...,"December"
         * @param month
         * @return
         * @throws JSONException
         */
        public Schedule addMonth( String month ) throws JSONException {
            JSONArray months = getMonths();
            months.put(month);
            return this;
        }

        /**
         * Get months where schedule is valid
         * @return
         */
        public JSONArray getMonths() throws JSONException {
            if( ! this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_MONTH) ) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_MONTH, new JSONArray());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONArray(TRIGGER_MONTH);
        }

        public JSONObject getRandom() throws JSONException {
            if( ! this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_RANDOM) ) {
                this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_RANDOM, new JSONObject());
            }
            return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getJSONObject(TRIGGER_RANDOM);
        }

        /**
         * Listen for this contextual broadcast to trigger this schedule
         * @param broadcast e.g., ACTION_AWARE_CALL_ACCEPTED runs this schedule when the user has answered a phone call
         */
        public void setContext( String broadcast ) throws JSONException {
            this.schedule.getJSONObject(SCHEDULE_TRIGGER).put(TRIGGER_CONTEXT, broadcast);
        }

        /**
         * Get the contextual broadcast that triggers this schedule
         * @return
         */
        public String getContext() throws JSONException {
            if( this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_CONTEXT) ) {
                return this.schedule.getJSONObject(SCHEDULE_TRIGGER).getString(TRIGGER_CONTEXT);
            }
            return "";
        }

        /**
         * Get X random schedules from defined hour/weekday/month triggers
         * @throws JSONException
         */
        public void randomize(int random_amount) throws JSONException {
            JSONObject json_random = getRandom();
            Random random = new Random();

            if( this.schedule.getJSONObject(SCHEDULE_TRIGGER).has(TRIGGER_HOUR) ) {
                JSONArray valid_hours = getHours();
                JSONArray selected = new JSONArray();
                while( selected.length() < random_amount ) {
                    int selected_random = valid_hours.getInt(random.nextInt(valid_hours.length()));
                    boolean is_repeated = false;
                    for( int i=0; i < selected.length(); i++) {
                        if( selected.getInt(i) == selected_random ) is_repeated = true;
                    }
                    if( ! is_repeated ) selected.put(selected_random);
                }
                json_random.put(RANDOM_HOUR, selected );
            }
            if( this.trigger.has(TRIGGER_MONTH) ) {
                JSONArray valid_months = getMonths();
                JSONArray selected = new JSONArray();
                while( selected.length() < random_amount ) {
                    String selected_random = valid_months.getString(random.nextInt(valid_months.length()));
                    boolean is_repeated = false;
                    for( int i=0; i < selected.length(); i++) {
                        if( selected.getString(i).equals(selected_random) ) is_repeated = true;
                    }
                    if( ! is_repeated ) selected.put(selected_random);
                }
                json_random.put(RANDOM_MONTH, selected );
            }
            if( this.trigger.has(TRIGGER_WEEKDAY) ) {
                JSONArray valid_weekdays = getWeekdays();
                JSONArray selected = new JSONArray();
                while( selected.length() < random_amount ) {
                    String selected_random = valid_weekdays.getString(random.nextInt(valid_weekdays.length()));
                    boolean is_repeated = false;
                    for( int i=0; i < selected.length(); i++) {
                        if( selected.getString(i).equals(selected_random) ) is_repeated = true;
                    }
                    if( ! is_repeated ) selected.put(selected_random);
                }
                json_random.put(RANDOM_WEEKDAY, selected );
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Check if we have anything scheduled
        Cursor scheduled_tasks = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, null, null, Scheduler_Provider.Scheduler_Data.TIMESTAMP + " ASC");
        if( scheduled_tasks != null && scheduled_tasks.moveToFirst() ) {
            do {
                try {

                    final Schedule schedule = new Schedule(scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE_ID)));
                    schedule.rebuild(new JSONObject(scheduled_tasks.getString(scheduled_tasks.getColumnIndex(Scheduler_Provider.Scheduler_Data.SCHEDULE))));

                    //restore all contextual schedulers
                    if( schedule.getContext().length() > 0 ) {

                        BroadcastReceiver listener = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                if( Aware.DEBUG ) Log.d(Aware.TAG, "Received scheduler broadcast");
                                performAction( schedule );
                            }
                        };

                        IntentFilter filter = new IntentFilter(schedule.getContext());

                        Hashtable<IntentFilter, BroadcastReceiver> scheduler_listener = new Hashtable<>();
                        scheduler_listener.put(filter, listener);
                        schedulerListeners.put(schedule.getScheduleID(), scheduler_listener);

                        registerReceiver(listener, filter);

                        if( Aware.DEBUG ) Log.d(Aware.TAG, "Registered a contextual scheduler for " + schedule.getContext());

                    } else {
                        if( is_trigger( schedule ) ) {
                            if( Aware.DEBUG ) Log.d(Aware.TAG, "Triggering scheduled task: " + schedule.toString());
                            performAction( schedule );

                        } else {
                            if( Aware.DEBUG ) Log.d(Aware.TAG, "Task schedule not matched: " + schedule.build().toString(1));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (scheduled_tasks.moveToNext());
        }
        if( scheduled_tasks != null && ! scheduled_tasks.isClosed()) scheduled_tasks.close();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for( String schedule_id : schedulerListeners.keySet() ) {
            Hashtable<IntentFilter, BroadcastReceiver> scheduled = schedulerListeners.get(schedule_id);
            for( IntentFilter filter : scheduled.keySet() ) {
                try {
                    unregisterReceiver( scheduled.get(filter) );
                } catch (NullPointerException e ) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean is_trigger ( Schedule schedule ) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        try {
            Log.d(Aware.TAG, "Hours:" + schedule.getHours().toString(1) + " Length:" + schedule.getHours().length());
            Log.d(Aware.TAG, "Weekday:" + schedule.getWeekdays().toString(1) + " Length:" + schedule.getWeekdays().length());
            Log.d(Aware.TAG, "Months:" + schedule.getMonths().toString(1) + " Length:" + schedule.getMonths().length());
        } catch (JSONException e ) {
            e.printStackTrace();
        }

        try {
            long last_triggered = 0;
            Cursor last_time_triggered = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, new String[]{Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED}, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null, null);
            if( last_time_triggered != null && last_time_triggered.moveToFirst() ) {
                last_triggered = last_time_triggered.getLong(last_time_triggered.getColumnIndex(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED));
            }
            if( last_time_triggered != null && ! last_time_triggered.isClosed() ) last_time_triggered.close();

            //This is a scheduled task with a precise time
            if( schedule.getTimer() != -1 && last_triggered == 0) { //not been triggered yet
                Log.d(Aware.TAG, "Checking trigger set for a specific timestamp");
                long trigger_time = schedule.getTimer();
                if( (now.getTimeInMillis()-trigger_time) < 5*60*1000 ) return true;
            }

            //triggered at the given hours, regardless of weekday or month
            if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() == 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given hour, regardless of weekday or month");
                return is_trigger_hour(schedule, last_triggered);
            //triggered at given hours and week day
            } else if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() == 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given hour, and weekday");
                return is_trigger_hour(schedule, last_triggered) && is_trigger_weekday(schedule, last_triggered);
            //triggered at given hours, week day and month
            } else if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() > 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given hour, weekday and month");
                return is_trigger_hour(schedule, last_triggered) && is_trigger_weekday(schedule, last_triggered) && is_trigger_month(schedule, last_triggered);
            //triggered at given weekday, regardless of time or month
            } else if( schedule.getHours().length() == 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() == 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given weekday, regardless of hour or month");
                return is_trigger_weekday(schedule, last_triggered);
            //triggered at given weekday and month
            } else if( schedule.getHours().length() == 0 && schedule.getWeekdays().length() > 0 && schedule.getMonths().length() > 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given weekday, and month");
                return is_trigger_weekday(schedule, last_triggered) && is_trigger_month(schedule, last_triggered);
            //triggered at given month
            } else if( schedule.getHours().length() == 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() > 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given month");
                return is_trigger_month(schedule, last_triggered);
            //Triggered at given hour and months
            } else if( schedule.getHours().length() > 0 && schedule.getWeekdays().length() == 0 && schedule.getMonths().length() > 0 ) {
                Log.d(Aware.TAG, "Checking trigger at given hour and month");
                return is_trigger_hour(schedule, last_triggered) && is_trigger_month(schedule, last_triggered);
            }

        } catch (JSONException e ) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check if this trigger should be triggered at this hour
     * @param schedule
     * @param last_triggered
     * @return
     */
    private boolean is_trigger_hour( Schedule schedule, long last_triggered ) {

        if( Aware.DEBUG ) Log.d(Aware.TAG, "Checking hour matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(last_triggered);

        if( Aware.DEBUG ) Log.d(Aware.TAG, "Last time hour matched: " + previous.getTime().toString());

        try {
            JSONArray hours = schedule.getHours();
            for( int i=0; i<hours.length(); i++ ) {
                int hour = hours.getInt(i);

                if(Aware.DEBUG) {
                    Log.d(Aware.TAG, "Hour "+ hour +" vs now "+ now.get(Calendar.HOUR_OF_DAY) +" in trigger hours: " + hours.toString());
                }

                if( hour == now.get(Calendar.HOUR_OF_DAY) && last_triggered == 0 ) return true;
                if( hour == now.get(Calendar.HOUR_OF_DAY) && last_triggered != 0 && now.get(Calendar.HOUR_OF_DAY) > previous.get(Calendar.HOUR_OF_DAY) ) return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check if this schedule should be triggered this weekday
     * @param schedule
     * @param last_triggered
     * @return
     */
    private boolean is_trigger_weekday( Schedule schedule, long last_triggered ) {

        if( Aware.DEBUG ) Log.d(Aware.TAG, "Checking weekday matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(last_triggered);

        if( Aware.DEBUG ) Log.d(Aware.TAG, "Last time weekday matched: " + previous.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase());

        try {
            JSONArray weekdays = schedule.getWeekdays();
            for( int i=0; i<weekdays.length(); i++ ) {
                String weekday = weekdays.getString(i);

                if(Aware.DEBUG) {
                    Log.d(Aware.TAG, "Weekday "+weekday.toUpperCase()+" vs now "+ now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase() +" in trigger weekdays: " + weekdays.toString());
                }

                if( weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase()) && last_triggered == 0 ) return true;
                if( weekday.toUpperCase().equals(now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).toUpperCase()) && last_triggered != 0 && now.get(Calendar.WEEK_OF_YEAR) > previous.get(Calendar.WEEK_OF_YEAR) ) return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check if this schedule should be triggered this month
     * @param schedule
     * @param last_triggered
     * @return
     */
    private boolean is_trigger_month( Schedule schedule, long last_triggered ) {

        if( Aware.DEBUG ) Log.d(Aware.TAG, "Checking month matching");

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());

        Calendar previous = Calendar.getInstance();
        previous.setTimeInMillis(last_triggered);

        if( Aware.DEBUG ) Log.d(Aware.TAG, "Last time month matched: " + previous.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase());

        try {
            JSONArray months = schedule.getMonths();
            for( int i=0; i<months.length(); i++ ) {
                String month = months.getString(i);

                if(Aware.DEBUG) {
                    Log.d(Aware.TAG, "Month "+month.toUpperCase()+" vs now "+ now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase() +" in trigger months: " + months.toString());
                }

                if( month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase()) && last_triggered == 0 ) return true;
                if( month.toUpperCase().equals(now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).toUpperCase()) && last_triggered != 0 && now.get(Calendar.MONTH) > previous.get(Calendar.MONTH) ) return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void performAction( Schedule schedule ) {
        try {
            //Trigger a broadcast
            if (schedule.getActionType().equals(ACTION_TYPE_BROADCAST)) {
                Intent broadcast = new Intent(schedule.getActionClass());
                JSONArray extras = schedule.getActionExtras();
                for (int i=0; i<extras.length(); i++) {
                    JSONObject extra = extras.getJSONObject(i);

                    Object extra_obj = extra.get(ACTION_EXTRA_VALUE);
                    if( extra_obj instanceof String ) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    } else if( extra_obj instanceof Integer ) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getInt(ACTION_EXTRA_VALUE));
                    } else if( extra_obj instanceof Double ) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getDouble(ACTION_EXTRA_VALUE));
                    }else if( extra_obj instanceof Long ) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getLong(ACTION_EXTRA_VALUE));
                    }else if( extra_obj instanceof Boolean ) {
                        broadcast.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getBoolean(ACTION_EXTRA_VALUE));
                    }
                }
                sendBroadcast(broadcast);
            //Trigger an activity
            } else if (schedule.getActionType().equals(ACTION_TYPE_ACTIVITY)) {
                try {
                    Class<?> activity_class = Class.forName(schedule.getActionClass());
                    Intent activity = new Intent(getApplicationContext(), activity_class);
                    activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    JSONArray extras = schedule.getActionExtras();
                    for (int i = 0; i < extras.length(); i++) {
                        JSONObject extra = extras.getJSONObject(i);
                        activity.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    }
                    startActivity(activity);

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            //Trigger a service
            } else if (schedule.getActionType().equals(ACTION_TYPE_SERVICE)) {
                try {
                    Class<?> service_class = Class.forName(schedule.getActionClass());
                    Intent service = new Intent(getApplicationContext(), service_class);
                    service.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    JSONArray extras = schedule.getActionExtras();
                    for (int i = 0; i < extras.length(); i++) {
                        JSONObject extra = extras.getJSONObject(i);
                        service.putExtra(extra.getString(ACTION_EXTRA_KEY), extra.getString(ACTION_EXTRA_VALUE));
                    }
                    startService(service);

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            Toast.makeText(getApplicationContext(), "Triggered " + schedule.getActionType() + "\n" + schedule.getActionClass(), Toast.LENGTH_SHORT).show();

            ContentValues data = new ContentValues();
            data.put(Scheduler_Provider.Scheduler_Data.LAST_TRIGGERED, System.currentTimeMillis());
            getContentResolver().update(Scheduler_Provider.Scheduler_Data.CONTENT_URI, data, Scheduler_Provider.Scheduler_Data.SCHEDULE_ID + " LIKE '" + schedule.getScheduleID() + "'", null);

        }catch (JSONException e ){
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
