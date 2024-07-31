package com.maknoon.audiocataloger;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;

//https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri
// Check enhancement from https://github.com/Blankj/AndroidUtilCode/blob/51c8124045ec5b7c6d342b94a4020ed2f7fda496/lib/utilcode/src/main/java/com/blankj/utilcode/util/UriUtils.java#L97
public final class UriUtil
{
    private static final String PRIMARY_VOLUME_NAME = "primary";

    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con)
    {
        if (treeUri == null)
            return null;

        String p = treeUri.toString();
        if (p.contains("com.android.providers.downloads")) // for API less than 30
        {
            if (p.contains("raw"))
                return Uri.decode(p.substring(p.indexOf("raw%3A") + 6));
            else
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        }

        if (p.contains("content://com.android.externalstorage.documents/tree/home%3A")) // for API less than 30
        {
            p = p.replace("content://com.android.externalstorage.documents/tree/home%3A", "");
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath()
                    + (p.isEmpty() ? "" : File.separator + Uri.decode(p));
        }

        String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri), con);
        if (volumePath == null)
            return null;

        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0)
        {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        }
        else
            return volumePath;
    }

    private static String getVolumePath(final String volumeId, Context context)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return getVolumePathForAndroid11AndAbove(volumeId, context);
        else
            return getVolumePathBeforeAndroid11(volumeId, context);
    }

    private static String getVolumePathBeforeAndroid11(final String volumeId, Context context)
    {
        try
        {
            final StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            final Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            final Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            final Method getUuid = storageVolumeClazz.getMethod("getUuid");
            final Method getPath = storageVolumeClazz.getMethod("getPath");
            final Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            final Object result = getVolumeList.invoke(mStorageManager);

            if(result != null)
            {
                final int length = Array.getLength(result);
                for (int i = 0; i < length; i++)
                {
                    final Object storageVolumeElement = Array.get(result, i);
                    final String uuid = (String) getUuid.invoke(storageVolumeElement);
                    final Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                    if (Boolean.TRUE.equals(primary) && PRIMARY_VOLUME_NAME.equals(volumeId)) // primary volume?
                        return (String) getPath.invoke(storageVolumeElement);

                    if (uuid != null && uuid.equals(volumeId)) // other volumes?
                        return (String) getPath.invoke(storageVolumeElement);
                }
            }

            // not found.
            return null;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static String getVolumePathForAndroid11AndAbove(final String volumeId, Context context)
    {
        try
        {
            final StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (mStorageManager == null)
                return null;

            final List<StorageVolume> storageVolumes = mStorageManager.getStorageVolumes();
            for (StorageVolume storageVolume : storageVolumes)
            {
                // primary volume?
                if (storageVolume.isPrimary() && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return storageVolume.getDirectory().getPath();

                // other volumes?
                final String uuid = storageVolume.getUuid();
                if (uuid != null && uuid.equals(volumeId))
                    return storageVolume.getDirectory().getPath();
            }

            // not found.
            return null;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri)
    {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0)
            return split[0];
        else
            return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri treeUri)
    {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null))
            return split[1];
        else
            return File.separator;
    }
}