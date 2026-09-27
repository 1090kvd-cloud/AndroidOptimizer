package cloud.kvd.androidoptimizer;
import android.os.Bundle;
interface IPackageControl {
    void destroy() = 16777114;
    Bundle changePackage(String packageName, int userId, boolean enabled) = 1;
}
