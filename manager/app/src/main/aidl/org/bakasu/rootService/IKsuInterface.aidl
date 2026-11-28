// IKsuInterface.aidl
package org.bakasu.rootService;

import android.content.pm.PackageInfo;
import android.os.ParcelFileDescriptor;
import java.util.List;

interface IKsuInterface {
    int getPackageCount();
    List<PackageInfo> getPackages(int start, int maxCount);
    ParcelFileDescriptor openFile(String path, int mode);
}
