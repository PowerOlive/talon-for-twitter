package com.klinker.android.twitter.data.sq_lite;


import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.util.Log;
import com.klinker.android.twitter.R;
import com.klinker.android.twitter.utils.TweetLinkUtils;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.Calendar;
import java.util.List;

public class ActivityDataSource {

    public static final int TYPE_MENTION = 0;
    public static final int TYPE_NEW_FOLLOWER = 1;
    public static final int TYPE_RETWEETS = 2;
    public static final int TYPE_FAVORITES = 3;

    // provides access to the database
    public static ActivityDataSource dataSource = null;

    public static ActivityDataSource getInstance(Context context) {
        // if the datasource isn't open or it the object is null
        if (dataSource == null ||
                dataSource.getDatabase() == null ||
                !dataSource.getDatabase().isOpen()) {

            dataSource = new ActivityDataSource(context); // create the database
            dataSource.open(); // open the database
        }

        return dataSource;
    }

    // Database fields
    private SQLiteDatabase database;
    private ActivitySQLiteHelper dbHelper;
    private SharedPreferences sharedPrefs;
    private Context context;
    
    public String[] allColumns = {
            ActivitySQLiteHelper.COLUMN_ID, ActivitySQLiteHelper.COLUMN_TITLE, ActivitySQLiteHelper.COLUMN_TWEET_ID, ActivitySQLiteHelper.COLUMN_ACCOUNT, ActivitySQLiteHelper.COLUMN_TYPE,
            ActivitySQLiteHelper.COLUMN_TEXT, ActivitySQLiteHelper.COLUMN_NAME, ActivitySQLiteHelper.COLUMN_PRO_PIC,
            ActivitySQLiteHelper.COLUMN_SCREEN_NAME, ActivitySQLiteHelper.COLUMN_TIME, ActivitySQLiteHelper.COLUMN_PIC_URL,
            ActivitySQLiteHelper.COLUMN_RETWEETER, ActivitySQLiteHelper.COLUMN_URL, HomeSQLiteHelper.COLUMN_USERS, HomeSQLiteHelper.COLUMN_HASHTAGS, ActivitySQLiteHelper.COLUMN_ANIMATED_GIF,
            ActivitySQLiteHelper.COLUMN_CONVERSATION, ActivitySQLiteHelper.COLUMN_FAV_COUNT, ActivitySQLiteHelper.COLUMN_RETWEET_COUNT };

    public ActivityDataSource(Context context) {
        dbHelper = new ActivitySQLiteHelper(context);
        sharedPrefs = context.getSharedPreferences("com.klinker.android.twitter_world_preferences",
                Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE);
        this.context = context;
    }

    public void open() throws SQLException {

        try {
            database = dbHelper.getWritableDatabase();
        } catch (Exception e) {
            close();
        }
    }

    public void close() {
        try {
            dbHelper.close();
        } catch (Exception e) {

        }
        database = null;
        dataSource = null;
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public ActivitySQLiteHelper getHelper() {
        return dbHelper;
    }

    public synchronized void insertMention(Status status, int account) {
        ContentValues values = getValues(status, account, TYPE_MENTION);

        try {
            database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
        } catch (Exception e) {
            open();
            database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
        }
    }

    public synchronized int insertMentions(List<Status> statuses, int account) {

        ContentValues[] valueses = new ContentValues[statuses.size()];

        for (int i = 0; i < statuses.size(); i++) {
            ContentValues values = getValues(statuses.get(i), account, TYPE_MENTION);
            valueses[i] = values;
        }

        return insertMultiple(valueses);
    }

    public synchronized boolean insertRetweeters(Status status, int account, Twitter twitter) {
        int retweetCountInDb = retweetExists(status.getId(), account);
        if (retweetCountInDb != -1 && retweetCountInDb < status.getRetweetCount()) {
            // we want to update the current
            ContentValues values = getRetweeterContentValues(status, account, twitter);
            if (values != null) {
                try {
                    database.update(
                            ActivitySQLiteHelper.TABLE_ACTIVITY,
                            values,
                            ActivitySQLiteHelper.COLUMN_TWEET_ID + " = ? AND " + ActivitySQLiteHelper.COLUMN_ACCOUNT + " = ?",
                            new String[]{status.getId() + "", account + ""}
                    );
                } catch (Exception e) {
                    open();
                    database.update(
                            ActivitySQLiteHelper.TABLE_ACTIVITY,
                            values,
                            ActivitySQLiteHelper.COLUMN_TWEET_ID + " = ? AND " + ActivitySQLiteHelper.COLUMN_ACCOUNT + " = ?",
                            new String[]{status.getId() + "", account + ""}
                    );
                }
            } else {
                return false;
            }

            return true;
        } else if (status.getRetweetCount() > 0 && retweetCountInDb == -1) {
            ContentValues values = getRetweeterContentValues(status, account, twitter);
            if (values != null) {
                try {
                    database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
                } catch (Exception e) {
                    open();
                    database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
                }
            } else {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    public synchronized boolean insertFavoriteCount(Status status, int account) {
        int favCountInDb = favoriteExists(status.getId(), account);

        Log.v("ActivityUtils", "tweet: " + status.getText());
        Log.v("ActivityUtils", "favorite count in database: " + favCountInDb);
        Log.v("ActivityUtils", "favorite count on tweet: " + status.getFavoriteCount());

        if (favCountInDb != -1 && favCountInDb < status.getFavoriteCount()) {
            // we want to update the current
            ContentValues values = getFavoriteValues(status, account);
            try {
                database.update(
                        ActivitySQLiteHelper.TABLE_ACTIVITY,
                        values,
                        ActivitySQLiteHelper.COLUMN_TWEET_ID + " = ? AND " + ActivitySQLiteHelper.COLUMN_ACCOUNT + " = ?",
                        new String[]{status.getId() + "", account + ""}
                );
            } catch (Exception e) {
                open();
                database.update(
                        ActivitySQLiteHelper.TABLE_ACTIVITY,
                        values,
                        ActivitySQLiteHelper.COLUMN_TWEET_ID + " = ? AND " + ActivitySQLiteHelper.COLUMN_ACCOUNT + " = ?",
                        new String[]{status.getId() + "", account + ""}
                );
            }

            return true;
        } else if (status.getFavoriteCount() > 0 && favCountInDb == -1) {
            // it isn't in the database yet
            ContentValues values = getFavoriteValues(status, account);
            try {
                database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
            } catch (Exception e) {
                open();
                database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
            }

            return true;
        } else {
            return false;
        }
    }

    public synchronized void insertNewFollower(User u, int account) {
        ContentValues values = getNewFollowerValues(u, account);

        try {
            database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
        } catch (Exception e) {
            open();
            database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
        }
    }

    public ContentValues getValues(Status status, int account, int type) {
        ContentValues values = new ContentValues();
        String originalName = "";
        long id = status.getId();
        long time = status.getCreatedAt().getTime();

        String[] html = TweetLinkUtils.getLinksInStatus(status);
        String text = html[0];
        String media = html[1];
        String otherUrl = html[2];
        String hashtags = html[3];
        String users = html[4];

        if (media.contains("/tweet_video/")) {
            media = media.replace("tweet_video", "tweet_video_thumb").replace(".mp4", ".png");
        }

        values.put(ActivitySQLiteHelper.COLUMN_TITLE, "@" + status.getUser().getScreenName() + " " + context.getString(R.string.mentioned_you));
        values.put(ActivitySQLiteHelper.COLUMN_ACCOUNT, account);
        values.put(ActivitySQLiteHelper.COLUMN_TEXT, text);
        values.put(ActivitySQLiteHelper.COLUMN_TWEET_ID, id);
        values.put(ActivitySQLiteHelper.COLUMN_NAME, status.getUser().getName());
        values.put(ActivitySQLiteHelper.COLUMN_PRO_PIC, status.getUser().getOriginalProfileImageURL());
        values.put(ActivitySQLiteHelper.COLUMN_SCREEN_NAME, status.getUser().getScreenName());
        values.put(ActivitySQLiteHelper.COLUMN_TIME, time);
        values.put(ActivitySQLiteHelper.COLUMN_RETWEETER, originalName);
        values.put(ActivitySQLiteHelper.COLUMN_PIC_URL, media);
        values.put(ActivitySQLiteHelper.COLUMN_URL, otherUrl);
        values.put(ActivitySQLiteHelper.COLUMN_PIC_URL, media);
        values.put(ActivitySQLiteHelper.COLUMN_USERS, users);
        values.put(ActivitySQLiteHelper.COLUMN_HASHTAGS, hashtags);
        values.put(ActivitySQLiteHelper.COLUMN_TYPE, type);
        values.put(ActivitySQLiteHelper.COLUMN_ANIMATED_GIF, TweetLinkUtils.getGIFUrl(status, otherUrl));
        values.put(HomeSQLiteHelper.COLUMN_CONVERSATION, status.getInReplyToStatusId() == -1 ? 0 : 1);
        values.put(ActivitySQLiteHelper.COLUMN_FAV_COUNT, status.getFavoriteCount());
        values.put(ActivitySQLiteHelper.COLUMN_RETWEET_COUNT, status.getRetweetCount());

        return values;
    }

    public ContentValues getFavoriteValues(Status status, int account) {
        ContentValues values = new ContentValues();

        long id = status.getId();

        String[] html = TweetLinkUtils.getLinksInStatus(status);
        String text = html[0];
        String media = html[1];
        String otherUrl = html[2];
        String hashtags = html[3];
        String users = html[4];

        if (media.contains("/tweet_video/")) {
            media = media.replace("tweet_video", "tweet_video_thumb").replace(".mp4", ".png");
        }

        values.put(ActivitySQLiteHelper.COLUMN_TITLE, status.getFavoriteCount() + " " + context.getString(R.string.favorites_lower));
        values.put(ActivitySQLiteHelper.COLUMN_ACCOUNT, account);
        values.put(ActivitySQLiteHelper.COLUMN_TEXT, text);
        values.put(ActivitySQLiteHelper.COLUMN_TWEET_ID, id);
        values.put(ActivitySQLiteHelper.COLUMN_NAME, status.getUser().getName());
        values.put(ActivitySQLiteHelper.COLUMN_PRO_PIC, status.getUser().getOriginalProfileImageURL());
        values.put(ActivitySQLiteHelper.COLUMN_SCREEN_NAME, status.getUser().getScreenName());
        values.put(ActivitySQLiteHelper.COLUMN_TIME, Calendar.getInstance().getTimeInMillis());
        values.put(ActivitySQLiteHelper.COLUMN_PIC_URL, media);
        values.put(ActivitySQLiteHelper.COLUMN_URL, otherUrl);
        values.put(ActivitySQLiteHelper.COLUMN_PIC_URL, media);
        values.put(ActivitySQLiteHelper.COLUMN_USERS, users);
        values.put(ActivitySQLiteHelper.COLUMN_HASHTAGS, hashtags);
        values.put(ActivitySQLiteHelper.COLUMN_TYPE, TYPE_FAVORITES);
        values.put(ActivitySQLiteHelper.COLUMN_ANIMATED_GIF, TweetLinkUtils.getGIFUrl(status, otherUrl));
        values.put(HomeSQLiteHelper.COLUMN_CONVERSATION, status.getInReplyToStatusId() == -1 ? 0 : 1);
        values.put(ActivitySQLiteHelper.COLUMN_FAV_COUNT, status.getFavoriteCount());
        values.put(ActivitySQLiteHelper.COLUMN_RETWEET_COUNT, status.getRetweetCount());

        return values;
    }

    public ContentValues getRetweeterContentValues(Status status, int account, Twitter twitter) {

        try {
            List<Status> retweets = twitter.getRetweets(status.getId());

            ContentValues values = new ContentValues();

            if (retweets.size() > 0) {
                values.put(ActivitySQLiteHelper.COLUMN_TITLE, buildRetweetersTitle(retweets));
                values.put(ActivitySQLiteHelper.COLUMN_ACCOUNT, account);
                values.put(ActivitySQLiteHelper.COLUMN_TEXT, context.getString(R.string.retweeted_your_status) + "\n" + status.getText());
                values.put(ActivitySQLiteHelper.COLUMN_TWEET_ID, status.getId());
                values.put(ActivitySQLiteHelper.COLUMN_PRO_PIC, retweets.get(0).getUser().getOriginalProfileImageURL());
                values.put(ActivitySQLiteHelper.COLUMN_TIME, retweets.get(0).getCreatedAt().getTime());
                values.put(ActivitySQLiteHelper.COLUMN_TYPE, TYPE_RETWEETS);
                values.put(ActivitySQLiteHelper.COLUMN_FAV_COUNT, status.getFavoriteCount());
                values.put(ActivitySQLiteHelper.COLUMN_RETWEET_COUNT, status.getRetweetCount());
            } else {
                return null;
            }

            return values;
        } catch (TwitterException e) {
            return null;
        }

    }

    public ContentValues getNewFollowerValues(User user, int account) {
        ContentValues values = new ContentValues();

        values.put(ActivitySQLiteHelper.COLUMN_TITLE, user.getName() + "(@" + user.getScreenName() + ")");
        values.put(ActivitySQLiteHelper.COLUMN_ACCOUNT, account);
        values.put(ActivitySQLiteHelper.COLUMN_TEXT, context.getString(R.string.followed_you));
        values.put(ActivitySQLiteHelper.COLUMN_TWEET_ID, user.getId());
        values.put(ActivitySQLiteHelper.COLUMN_NAME, user.getName());
        values.put(ActivitySQLiteHelper.COLUMN_PRO_PIC, user.getOriginalProfileImageURL());
        values.put(ActivitySQLiteHelper.COLUMN_SCREEN_NAME, user.getScreenName());
        values.put(ActivitySQLiteHelper.COLUMN_TIME, Calendar.getInstance().getTimeInMillis());
        values.put(ActivitySQLiteHelper.COLUMN_TYPE, TYPE_NEW_FOLLOWER);

        return values;
    }

    private String buildRetweetersTitle(List<Status> statuses) {
        String s = "";

        if (statuses.size() > 1) {
            s += "@" + statuses.get(0).getUser().getScreenName();
            for (int i = 1; i < statuses.size() - 1; i++) {
                s += ", @" + statuses.get(i).getUser().getScreenName();
            }
            s += " and @" + statuses.get(statuses.size() - 1).getUser().getScreenName();
        } else {
            // size equals 1
            s = "@" + statuses.get(0).getUser().getScreenName();
        }

        return s;
    }

    private synchronized int insertMultiple(ContentValues[] allValues) {
        int rowsAdded = 0;
        long rowId;
        ContentValues values;

        if (database == null || !database.isOpen()) {
            open();
        }

        try {
            database.beginTransaction();

            for (ContentValues initialValues : allValues) {
                values = initialValues == null ? new ContentValues() : new ContentValues(initialValues);
                try {
                    rowId = database.insert(ActivitySQLiteHelper.TABLE_ACTIVITY, null, values);
                } catch (IllegalStateException e) {
                    return rowsAdded;
                }
                if (rowId > 0)
                    rowsAdded++;
            }

            database.setTransactionSuccessful();
        } catch (NullPointerException e)  {
            e.printStackTrace();
            return rowsAdded;
        } catch (SQLiteDatabaseLockedException e) {
            e.printStackTrace();
            return rowsAdded;
        } catch (IllegalStateException e) {
            // caught setting up the transaction I guess, shouldn't ever happen now.
            e.printStackTrace();
            return rowsAdded;
        } finally {
            try {
                database.endTransaction();
            } catch (Exception e) {
                // shouldn't happen unless it gets caught above from an illegal state
            }
        }

        return rowsAdded;
    }

    public synchronized Cursor getCursor(int account) {

        String where = ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account;

        Cursor cursor;
        try {
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, where, null, null, null, ActivitySQLiteHelper.COLUMN_TIME + " ASC");
        } catch (Exception e) {
            open();
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, where, null, null, null, ActivitySQLiteHelper.COLUMN_TIME + " ASC");
        }

        return cursor;
    }

    public synchronized long[] getLastIds(int account) {
        long[] ids = new long[] {0, 0};

        Cursor cursor;
        try {
            cursor = getCursor(account);
        } catch (Exception e) {
            return ids;
        }

        try {
            if (cursor.moveToLast()) {
                ids[0] = cursor.getLong(cursor.getColumnIndex(ActivitySQLiteHelper.COLUMN_TWEET_ID));
            }

            if (cursor.moveToPrevious()) {
                ids[1] = cursor.getLong(cursor.getColumnIndex(ActivitySQLiteHelper.COLUMN_TWEET_ID));
            }
        } catch (Exception e) {

        }

        cursor.close();

        return ids;
    }

    public synchronized boolean tweetExists(long tweetId, int account) {
        Cursor cursor;
        try {
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account + " AND " + ActivitySQLiteHelper.COLUMN_TWEET_ID + " = " + tweetId, null, null, null, ActivitySQLiteHelper.COLUMN_TWEET_ID + " ASC");
        } catch (Exception e) {
            open();
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account + " AND " + ActivitySQLiteHelper.COLUMN_TWEET_ID + " = " + tweetId, null, null, null, ActivitySQLiteHelper.COLUMN_TWEET_ID + " ASC");
        }

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    /**
     * Find out if the tweet is in the database
     * @param tweetId
     * @param account
     * @return number of favorites on that tweet
     */
    public synchronized int favoriteExists(long tweetId, int account) {
        Cursor cursor;
        try {
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account + " AND " + ActivitySQLiteHelper.COLUMN_TWEET_ID + " = " + tweetId, null, null, null, ActivitySQLiteHelper.COLUMN_TWEET_ID + " ASC");
        } catch (Exception e) {
            open();
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account + " AND " + ActivitySQLiteHelper.COLUMN_TWEET_ID + " = " + tweetId, null, null, null, ActivitySQLiteHelper.COLUMN_TWEET_ID + " ASC");
        }

        if (cursor.moveToFirst()) {
            int count = cursor.getInt(cursor.getColumnIndex(ActivitySQLiteHelper.COLUMN_FAV_COUNT));
            try {
                cursor.close();
            } catch (Exception e) { }
            return count;
        } else {
            try {
                cursor.close();
            } catch (Exception e) { }
            return -1;
        }
    }

    /**
     * Find out if the tweet is in the database
     * @param tweetId
     * @param account
     * @return number of retweets on that tweet
     */
    public synchronized int retweetExists(long tweetId, int account) {
        Cursor cursor;
        try {
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account + " AND " + ActivitySQLiteHelper.COLUMN_TWEET_ID + " = " + tweetId, null, null, null, ActivitySQLiteHelper.COLUMN_TWEET_ID + " ASC");
        } catch (Exception e) {
            open();
            cursor = database.query(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    allColumns, ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account + " AND " + ActivitySQLiteHelper.COLUMN_TWEET_ID + " = " + tweetId, null, null, null, ActivitySQLiteHelper.COLUMN_TWEET_ID + " ASC");
        }

        if (cursor.moveToFirst()) {
            int count = cursor.getInt(cursor.getColumnIndex(ActivitySQLiteHelper.COLUMN_RETWEET_COUNT));
            try {
                cursor.close();
            } catch (Exception e) { }
            return count;
        } else {
            try {
                cursor.close();
            } catch (Exception e) { }
            return -1;
        }
    }

    public synchronized void deleteDups(int account) {
        try {
            database.execSQL("DELETE FROM " + ActivitySQLiteHelper.TABLE_ACTIVITY + " WHERE _id NOT IN (SELECT MIN(_id) FROM " + ActivitySQLiteHelper.TABLE_ACTIVITY + " GROUP BY " + ActivitySQLiteHelper.COLUMN_TWEET_ID + ") AND " + ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account);
        } catch (Exception e) {
            open();
            database.execSQL("DELETE FROM " + ActivitySQLiteHelper.TABLE_ACTIVITY + " WHERE _id NOT IN (SELECT MIN(_id) FROM " + ActivitySQLiteHelper.TABLE_ACTIVITY + " GROUP BY " + ActivitySQLiteHelper.COLUMN_TWEET_ID + ") AND " + ActivitySQLiteHelper.COLUMN_ACCOUNT + " = " + account);
        }
    }

    public synchronized void deleteItem(long id) {
        try {
            database.delete(HomeSQLiteHelper.TABLE_HOME, ActivitySQLiteHelper.COLUMN_ID
                    + " = " + id, null);
        } catch (Exception e) {
            open();
            database.delete(HomeSQLiteHelper.TABLE_HOME, ActivitySQLiteHelper.COLUMN_ID
                    + " = " + id, null);
        }
    }


    public synchronized void deleteAll(int account) {
        try {
            database.delete(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    HomeSQLiteHelper.COLUMN_ACCOUNT + " = " + account, null);
        } catch (Exception e) {
            open();
            database.delete(ActivitySQLiteHelper.TABLE_ACTIVITY,
                    HomeSQLiteHelper.COLUMN_ACCOUNT + " = " + account, null);
        }
    }
}