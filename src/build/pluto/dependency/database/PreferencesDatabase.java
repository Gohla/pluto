package build.pluto.dependency.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.sugarj.common.StringCommands;

public class PreferencesDatabase implements MultiMapDatabase<File, File> {
  private final static String PREFIX = "build.pluto";
  private static final char SEPC = 0;
  private static final String SEP = new String(new char[] { SEPC });

  private Preferences prefs;

  public PreferencesDatabase(String pathName) {
    final String path;
    if (pathName == null || pathName.isEmpty()) {
      path = PREFIX;
    } else {
      pathName = pathName.replace('\\', '/');
      if (pathName.startsWith("/")) {
        pathName = pathName.substring(1);
      }
      path = PREFIX + "/" + pathName;
    }
    this.prefs = Preferences.userRoot().node(path);
  }

  private String internalKey(File key) {
    String internalKey = key.getAbsolutePath();
    if (internalKey.length() > Preferences.MAX_KEY_LENGTH)
      internalKey = String.valueOf(key.hashCode());
    return internalKey;
  }

  private void set(File key, Collection<File> vals) {
    prefs.put(internalKey(key), StringCommands.printListSeparated(vals, SEP));
  }

  @Override
  public void add(File key, File val) {
    addAll(key, Collections.singleton(val));
  }

  @Override
  public void addAll(File key, Collection<? extends File> newVals) {
    Set<File> vals = new HashSet<>(get(key));
    vals.addAll(newVals);
    set(key, vals);
  }

  @Override
  public void addForEach(Collection<? extends File> keys, File val) {
    for (File key : keys)
      add(key, val);
  }

  @Override
  public boolean contains(File key, File val) {
    return get(key).contains(val);
  }

  @Override
  public Collection<File> get(File key) {
    String val = prefs.get(internalKey(key), null);
    if (val == null || val.isEmpty())
      return Collections.emptySet();

    if (val.charAt(0) != SEPC)
      return Collections.singleton(new File(val));

    String[] paths = val.substring(1).split(SEP);
    ArrayList<File> files = new ArrayList<>(paths.length);
    for (int i = 0; i < paths.length; i++)
      files.set(i, new File(paths[i]));
    return Collections.unmodifiableList(files);
  }

  @Override
  public void remove(File key, File val) {
    Set<File> vals = new HashSet<>(get(key));
    vals.remove(val);
    set(key, vals);
  }

  @Override
  public void removeAll(File key) {
    prefs.remove(internalKey(key));
  }

  @Override
  public void removeForEach(Collection<? extends File> keys, File val) {
    for (File key : keys)
      remove(key, val);
  }

  @Override
  public void clear() {
    try {
      prefs.clear();
      prefs.flush();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    try {
      prefs.sync();
    } catch (BackingStoreException e) {
      throw new IOException(e);
    }
    prefs = null;
  }
}
