package dk.jens.openbackup;

import android.support.v4.app.TaskStackBuilder;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.view.View.OnClickListener;
import android.view.View;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;

import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;

import android.graphics.Color;
import android.support.v4.app.NotificationCompat;
//import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;


public class BatchActivity extends Activity implements OnClickListener
{
    public static List<PackageInfo> pinfoList;
    final String TAG = "obackup";
    boolean backupBoolean;
    final static int SHOW_DIALOG = 0;
    final static int CHANGE_DIALOG = 1;
    final static int DISMISS_DIALOG = 2;

    final static int RESULT_OK = 0;

    boolean checkboxSelectAllBoolean = true;

    DoBackupRestore doBackupRestore = new DoBackupRestore();
    File backupDir;
    ProgressDialog progress;
    PackageManager pm;

    LinearLayout linearLayout;
    RadioButton rb, rbData, rbApk, rbBoth;
    ArrayList<CheckBox> checkboxList = new ArrayList<CheckBox>();
    HashMap<String, PackageInfo> pinfoMap = new HashMap<String, PackageInfo>();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.backuprestorelayout);

        pm = getPackageManager();

        backupDir = new File(Environment.getExternalStorageDirectory() + "/obackups");
        if(!backupDir.exists())
        {
            backupDir.mkdirs();
        }

        Bundle extra = getIntent().getExtras();
        if(extra != null)
        {
            backupBoolean = extra.getBoolean("dk.jens.openbackup.backupBoolean");
        }

        linearLayout = (LinearLayout) findViewById(R.id.backupLinear);

        Button bt = (Button) findViewById(R.id.backupRestoreButton);
        bt.setOnClickListener(this);
        if(backupBoolean)
        {
            bt.setText(R.string.backupButton);
        }
        else
        {
            bt.setText(R.string.restoreButton);
            RadioGroup rg = new RadioGroup(this);
            rg.setOrientation(LinearLayout.HORIZONTAL);
            rbData = new RadioButton(this);
            rbData.setText(R.string.radioData);
            rg.addView(rbData);
            rbData.setChecked(true); // hvis ikke setChecked() kaldes før  knappen er tilføjet til sin parent bliver den permanent trykket 
            rbApk = new RadioButton(this);
            rbApk.setText(R.string.radioApk);
            rg.addView(rbApk);
            rbBoth = new RadioButton(this);
            rbBoth.setText(R.string.radioBoth);
            rg.addView(rbBoth);
            linearLayout.addView(rg);
        }
//        rb = (RadioButton) findViewById(R.id.radioData);
//        rb.toggle();

//        PackageManager pm = getPackageManager();
//        List<PackageInfo> pinfoList = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        for(PackageInfo pinfo : pinfoList)
        {
            pinfoMap.put(pinfo.packageName, pinfo);
            setCheckBoxes(pinfo.packageName, Color.WHITE);
        }
        if(!backupBoolean)
        {
            for(String folder : backupDir.list())
            {
                if(!pinfoMap.containsKey(folder))
                {
                    setCheckBoxes(folder, Color.GRAY);
                }
            }
        }
    }
    @Override
    public void onClick(View v)
    {
        new Thread(new Runnable()
        {
            public void run()
            {
                int i = 0;
                for(CheckBox cb : checkboxList)
                {
                    if(cb.isChecked())
                    {
                        i++;
                    }
                }
                int id = (int) Calendar.getInstance().getTimeInMillis();
                int total = i;
                i = 0;
                for(CheckBox cb : checkboxList)
                {
                    if(cb.isChecked())
                    {
                        i++;
                        final PackageInfo pinfo;
                        final ApplicationInfo ainfo;
                        final String log;
                        final String packageName = cb.getText().toString();
                        String[] string = {packageName, Integer.toString(i), Integer.toString(total)};
                        File backupSubDir = new File(backupDir.getAbsolutePath() + "/" + packageName);
                        String title = backupBoolean ? "backing up" : "restoring";
                        showNotification(id, title, packageName);
                        if(i == 1)
                        {
                            // hvis ikke message instantieres hver gang bliver den ikkeresponsiv til sidst
                            Message message = Message.obtain();                                
                            message.what = SHOW_DIALOG;
                            message.obj = string;
                            handler.sendMessage(message);
                        }
                        else
                        {
                            Message message = Message.obtain();                                
                            message.what = CHANGE_DIALOG;
                            message.obj = string;
                            handler.sendMessage(message);
                        }
                        if(backupBoolean)
                        {
                            pinfo = pinfoMap.get(cb.getText().toString());
                            ainfo = pinfo.applicationInfo;
                            log = ainfo.loadLabel(pm).toString() + "\n" + pinfo.versionName + "\n" + pinfo.packageName + "\n" + ainfo.sourceDir + "\n" + ainfo.dataDir;
                            if(!backupSubDir.exists())
                            {
                                backupSubDir.mkdirs();
                            }
                            else
                            {
                                doBackupRestore.deleteBackup(backupSubDir);
                                backupSubDir.mkdirs();
                            }
                            doBackupRestore.doBackup(backupSubDir, ainfo.dataDir, ainfo.sourceDir);
                            doBackupRestore.writeLogFile(backupSubDir.getAbsolutePath() + "/" + pinfo.packageName + ".log", log);
                            Log.i(TAG, "backup: " + pinfo.packageName);
                        }
                        else
                        {
                            Log.i(TAG, "restore: " + packageName);
                            ArrayList<String> readlog = doBackupRestore.readLogFile(backupSubDir, packageName);
//                            String dataDir = "/data/data/" + readlog.get(2); // hele stien skal skrives til log
                            String dataDir = readlog.get(4); // når alle logfilerne er genskrevet
                            String apk = readlog.get(3);
                            String[] apkArray = apk.split("/");
                            apk = apkArray[apkArray.length - 1];

                            if(rbApk.isChecked())
                            {
                                doBackupRestore.restoreApk(backupSubDir, apk);
                            }
                            else if(rbData.isChecked())
                            {
                                if(pinfoMap.containsKey(packageName))
                                {
                                    doBackupRestore.doRestore(backupSubDir, packageName);
                                    doBackupRestore.setPermissions(dataDir);
                                }
                                else
                                {
                                    Log.i(TAG, "kan ikke doRestore uden restoreApk: " + packageName + " er ikke installeret");
                                }
                            }
                            else if(rbBoth.isChecked())
                            {
                                doBackupRestore.restoreApk(backupSubDir, apk);
                                doBackupRestore.doRestore(backupSubDir, packageName);                                
                                doBackupRestore.setPermissions(dataDir);
                            }
                        }
                        if(i == total)
                        {
                            String msg = backupBoolean ? "backup" : "restore";
                            showNotification(id, "operation complete", "batch " + msg);
                            Message message = Message.obtain();                                
                            message.what = DISMISS_DIALOG;
                            handler.sendMessage(message);

                        }
                    }
                }
            }
        }).start();
    }
    public void setCheckBoxes(String string, int color)
    {
        CheckBox cb = new CheckBox(this);
        cb.setText(string);
        cb.setTextColor(color);
        checkboxList.add(cb);
        linearLayout.addView(cb);
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        menu.clear();
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case R.id.de_selectAll:
                for(CheckBox cb : checkboxList)
                {
                    if(cb.isChecked() != checkboxSelectAllBoolean)
                    {
                        cb.toggle();
                    }
                }
                checkboxSelectAllBoolean = checkboxSelectAllBoolean ? false : true;
                break;
        }
        return true;
    }
    private Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message message)
        {
//            Log.i(TAG, message.toString());
            String[] array = (String[]) message.obj;
            switch(message.what)
            {
                case SHOW_DIALOG:
                    Log.i(TAG, "show");
                    progress = ProgressDialog.show(BatchActivity.this, array[0].toString(), "(" + array[1].toString() + "/" + array[2].toString() + ")", true, false); // den sidste boolean er cancelable -> sættes til true, når der er skrevet en måde at afbryde handlingen (threaden) på
                    break;
                case CHANGE_DIALOG:
                    if(progress != null)
                    {
                        progress.setTitle(array[0].toString());
                        progress.setMessage("(" + array[1].toString() + "/" + array[2].toString() + ")");
                    }
                    break;
                case DISMISS_DIALOG:
                    Log.i(TAG, "dismiss");
                    if(progress != null)
                    {
                        progress.dismiss();
                    }
                    break;
            }
        }
    };
    public void showNotification(int id, String title, String text)
    {
        // bør nok være det eksterne android.support.v4.app og NotificationCompat.Builder: http://developer.android.com/guide/topics/ui/notifiers/notifications.html
//        Notification.Builder mBuilder = new Notification.Builder(this)
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.backup_small)
            .setContentTitle(title)
            .setContentText(text);
        Intent resultIntent = new Intent(this, BatchActivity.class);        
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(BatchActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(id, mBuilder.build());
    }
}