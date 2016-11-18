package build.pluto.test.tracing;

import build.pluto.test.build.ScopedBuildTest;
import build.pluto.test.build.ScopedPath;
import build.pluto.test.build.cycle.fixpoint.FileUtils;
import build.pluto.tracing.*;
import org.junit.Ignore;
import org.junit.Test;
import org.sugarj.common.FileCommands;

import java.io.File;
import java.util.List;

/**
 * Created by manuel on 9/7/16.
 */
public class OutsideSTraceTest extends ScopedBuildTest {

    @ScopedPath("test.txt")
    private File file;

    @ScopedPath("test2.txt")
    private File file2;

    @ScopedPath("test3.txt")
    private File file3;

    @ScopedPath("test4.txt")
    private File file4;

    @Ignore
    @Test
    public void testTracing() throws Exception {
        ITracer t = new OutsideTracer();
        t.ensureStarted();
        assert(0 == FileUtils.readIntFromFile(file));
        List<FileDependency> deps = t.popDependencies();
        t.stop();
        System.out.println(deps);
        assert(deps.size() > 0);
        boolean foundFile = false;
        for (FileDependency d: deps) {
            if (d.getFile().getAbsoluteFile().equals(file.getAbsoluteFile()) && d.getMode() == FileAccessMode.READ_MODE)
                foundFile = true;
        }
        if (!foundFile) throw new Exception("Did not trace read file...");

        // Test restarting and popping dependencies
        t.ensureStarted();
        assert(0 == FileUtils.readIntFromFile(file2));
        List<FileDependency> deps1 = t.popDependencies();
        System.out.println(deps1);
        assert(deps1.size() > 0);
        foundFile = false;
        for (FileDependency d: deps1) {
            if (d.getFile().getAbsoluteFile().equals(file2.getAbsoluteFile()) && d.getMode() == FileAccessMode.READ_MODE)
                foundFile = true;
        }
        if (!foundFile) throw new Exception("Did not trace read file...");

        assert(1 == FileUtils.readIntFromFile(file3));

        List<FileDependency> deps2 = t.popDependencies();
        assert(deps2.size() > 0);
        foundFile = false;
        boolean foundWrongFile = false;
        for (FileDependency d: deps2) {
            if (d.getFile().getAbsoluteFile().equals(file3.getAbsoluteFile()) && d.getMode() == FileAccessMode.READ_MODE)
                foundFile = true;
            if (d.getFile().getAbsoluteFile().equals(file2.getAbsoluteFile()) && d.getMode() == FileAccessMode.READ_MODE)
                foundWrongFile = true;
        }
        if (!foundFile) throw new Exception("Did not trace read file2...");
        if (foundWrongFile) throw new Exception("Did trace read file that should have been popped...");

        // Test detecting write dependencies...
        FileCommands.writeToFile(file4, "hello world");
        deps = t.popDependencies();
        t.stop();
        System.out.println(deps);
        assert(deps.size() > 0);
        foundFile = false;
        for (FileDependency d: deps) {
            if (d.getFile().getAbsoluteFile().equals(file4.getAbsoluteFile()) && d.getMode() == FileAccessMode.WRITE_MODE)
                foundFile = true;
        }
        if (!foundFile) throw new Exception("Did not trace written file...");
    }
}
