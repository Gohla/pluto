package build.pluto.builder;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sugarj.common.Log;

import build.pluto.util.AbsoluteComparedFile;
import build.pluto.util.UniteCollections;

public class RequireStack extends CycleDetectionStack<BuildRequest<?, ?, ?, ?>, Boolean> {

  private Set<BuildRequest<?, ?, ?, ?>> knownInconsistentUnits;
  private Set<BuildRequest<?, ?, ?, ?>> knownConsistentUnits;
  
  public RequireStack() {
    this.knownConsistentUnits = new HashSet<>();
    this.knownInconsistentUnits = new HashSet<>();
  }

  public void beginRebuild(BuildRequest<?, ?, ?, ?> dep) {
    
    Log.log.log("Rebuild " + dep.createBuilder().description(), Log.DETAIL);
    
    this.knownInconsistentUnits.add(dep);
    // TODO: Need to forget the scc where dep is in, because the graph structure
    // may change?
   // for (Path assumed : this.requireStack) {
      // This could be too strict
      // this.knownInconsistentUnits.add(assumed);
     // this.consistentUnits.remove(assumed);
    //}
  }

  private String printCyclicConsistentAssumtion(BuildRequest<?, ?, ?, ?> dep) {
    UniteCollections<BuildRequest<?, ?, ?, ?>, List<BuildRequest<?, ?, ?, ?>>>.Key key = this.sccs.getSet(dep);
    if (key == null) {
      return "";
    }
    String s = "";
    for (BuildRequest<?, ?, ?, ?> p : this.sccs.getSetMembers(key)) {
      s += p.createBuilder().description() + ", ";
    }
    return s;
  }

  public void finishRebuild(BuildRequest<?, ?, ?, ?> dep) {
    this.knownConsistentUnits.add(dep);
    this.knownInconsistentUnits.remove(dep);
    // Allowed to do that in any case?
    // this.assumedCyclicConsistency.remove(dep);
  }

  public boolean isKnownInconsistent(File dep) {
    AbsoluteComparedFile aDep = AbsoluteComparedFile.absolute(dep);
    return knownInconsistentUnits.contains(aDep);
  }

  public boolean existsInconsistentCyclicRequest(BuildRequest<?, ?, ?, ?> dep) {
    return this.sccs.getSetMembers(dep).stream().anyMatch(this.knownInconsistentUnits::contains);
  }

  public boolean areAllCyclicRequestsConsistent(BuildRequest<?, ?, ?, ?> dep) {
    return this.sccs.getSetMembers(dep).stream().allMatch(this.knownConsistentUnits::contains);
  }

  public BuildCycle createCycleFor(BuildRequest<?, ?, ?, ?> dep) {
    List<BuildRequest<?, ?, ?, ?>> deps = sccs.getSetMembers(sccs.getSet(dep));
    return new BuildCycle(dep, deps);
  }

  public boolean isConsistent(BuildRequest<?, ?, ?, ?> dep) {
    return this.knownConsistentUnits.contains(dep);
  }
  
  @Override
  protected Boolean push(BuildRequest<?, ?, ?, ?> dep) {
    Log.log.beginTask("Require " + dep.createBuilder().description(), Log.DETAIL);
    return super.push(dep);
  }

  @Override
  public void pop(BuildRequest<?, ?, ?, ?> dep) {
    super.pop(dep);
    Log.log.endTask();
  }

  public void markConsistent(BuildRequest<?, ?, ?, ?> dep) {
    this.knownConsistentUnits.add(dep);
  }

  @Override
  protected Boolean cycleResult(BuildRequest<?, ?, ?, ?> call, List<BuildRequest<?, ?, ?, ?>> scc) {
    Log.log.log("Already required " + call.createBuilder().description(), Log.DETAIL);
    this.callStack.add(call);
    return true;
  }

  @Override
  protected Boolean noCycleResult() {
    return false;
  }

}
