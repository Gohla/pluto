package org.sugarj.cleardep.build;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sugarj.cleardep.BuildUnit;
import org.sugarj.cleardep.build.RequiredBuilderFailed.BuilderResult;
import org.sugarj.cleardep.dependency.BuildRequirement;
import org.sugarj.cleardep.dependency.DuplicateBuildUnitPathException;
import org.sugarj.cleardep.dependency.DuplicateFileGenerationException;
import org.sugarj.cleardep.dependency.FileRequirement;
import org.sugarj.cleardep.dependency.Requirement;
import org.sugarj.cleardep.output.BuildOutput;
import org.sugarj.cleardep.stamp.Stamp;
import org.sugarj.common.Log;
import org.sugarj.common.path.Path;

import com.cedarsoftware.util.DeepEquals;

public class BuildManager {

  private final static Map<Thread, BuildManager> activeManagers = new HashMap<>();
  
  public static <Out extends BuildOutput> Out build(BuildRequest<?, Out, ?, ?> buildReq) throws IOException {
    return build(buildReq, null);
  }
  
  public static <Out extends BuildOutput> Out build(BuildRequest<?, Out, ?, ?> buildReq, Map<? extends Path, Stamp> editedSourceFiles) throws IOException {
    Thread current = Thread.currentThread();
    BuildManager manager = activeManagers.get(current);
    boolean freshManager = manager == null;
    if (freshManager) {
      manager = new BuildManager(editedSourceFiles);
      activeManagers.put(current, manager);
    }
    
    try {
      return manager.require(buildReq).getBuildResult();
    } finally {
      if (freshManager)
        activeManagers.remove(current);
    }
  }
  
  public static <Out extends BuildOutput> List<Out> buildAll(BuildRequest<?, Out, ?, ?>[] buildReqs) throws IOException {
    return buildAll(buildReqs, null);
  }
  
  public static <Out extends BuildOutput> List<Out> buildAll(BuildRequest<?, Out, ?, ?>[] buildReqs, Map<? extends Path, Stamp> editedSourceFiles) throws IOException {
    Thread current = Thread.currentThread();
    BuildManager manager = activeManagers.get(current);
    boolean freshManager = manager == null;
    if (freshManager) {
      manager = new BuildManager(editedSourceFiles);
      activeManagers.put(current, manager);
    }
    
    try {
      List<Out> out = new ArrayList<>();
      for (BuildRequest<?, Out, ?, ?> buildReq : buildReqs)
        out.add(manager.require(buildReq).getBuildResult());
      return out;
    } finally {
      if (freshManager)
        activeManagers.remove(current);
    }
  }
  
  private final Map<? extends Path, Stamp> editedSourceFiles;
  private RequireStack requireStack;

  private BuildRequest<?, ?, ?, ?> rebuildTriggeredBy = null;

  private Set<BuildUnit<?>> consistentUnits;
  private Map<Path, BuildUnit<?>> generatedFiles;
  
  protected BuildManager(Map<? extends Path, Stamp> editedSourceFiles) {
    this.editedSourceFiles = editedSourceFiles;
    this.requireStack = new RequireStack();
    this.consistentUnits = new HashSet<>();
    this.generatedFiles = new HashMap<>();
  }

  protected Builder<?, ?> getCyclicBuilder(List<BuildRequirement<?>> cycle) {
    for (BuildRequirement<?> req : cycle) {
      Builder<?, ?> tmp = req.req.createBuilder();
      if (tmp.canBuildCycle(cycle)) {
        return tmp;
      }
    }
    return null;
  }
  
  protected <
  In extends Serializable, 
  Out extends BuildOutput, 
  B extends Builder<In, Out>, 
  F extends BuilderFactory<In, Out, B>
    > BuildUnit<Out> executeBuilder(Builder<In, Out> builder, Path dep, BuildRequest<In, Out, B, F> buildReq) throws IOException {

    BuildUnit<Out> depResult = BuildUnit.create(dep, buildReq);
    int inputHash = DeepEquals.deepHashCode(builder.input);

    String taskDescription = builder.taskDescription();
    if (taskDescription != null)
      Log.log.beginTask(taskDescription, Log.CORE);

    // First step: cycle detection
    BuildStackEntry entry = null;

    try {
      entry = this.requireStack.push(buildReq, dep);
    } catch (BuildCycleException e) {
      // Here is a cycle, abort without doing anything to Build Units because we
      // want to use the
      // BuildUnit which resulted from the first call on the dep path
      if (taskDescription != null) {
        Log.log.log("Aborted because of detected cycle", Log.CORE);
        Log.log.endTask();
      }
      throw e;
    }

    try {
      depResult.setState(BuildUnit.State.IN_PROGESS);

      // call the actual builder
      try {
        Out out = builder.triggerBuild(depResult, this);
        depResult.setBuildResult(out);
        
        if (!depResult.isFinished())
          depResult.setState(BuildUnit.State.SUCCESS);
        // build(depResult, input);
      } catch (BuildCycleException e) {
        if (e.isUnitFirstInvokedOn(dep, buildReq.factory)) {
          if (taskDescription != null)
            Log.log.endTask();
          taskDescription = "Try to compile cycle";
          Log.log.beginTask(taskDescription, Log.CORE);
          e.addCycleComponent(new BuildRequirement<>(depResult, buildReq));

          // Get the cycle and try to compile it
          List<BuildRequirement<?>> cycle = e.getCycleComponents();
          Builder<?, ?> cycleBuilder = getCyclicBuilder(cycle);
          if (cycleBuilder == null) {
            Log.log.logErr("Unable to find builder which can compile the cycle", Log.CORE);
            // Cycle cannot be handled
            throw new RequiredBuilderFailed(builder, depResult, e);
          }
          Log.log.log(cycleBuilder.cyclicTaskDescription(cycle), Log.CORE);
          cycleBuilder.buildCycle(cycle);
          // Do not throw anything here because cycle is completed successfully.
        } else {
          throw e;
        }
      }

    } catch (BuildCycleException e) {
      // This is the exception which has been rethrown above, but we cannot
      // handle it
      // here because compiling the cycle needs to be in the major try block
      // where normal
      // units are compiled too
      if (e.isUnitFirstInvokedOn(dep, buildReq.factory)) {
        // here we should never get because this case in handled in the inner
        // try
        throw new AssertionError("should not get there");
      } else {
        // Collect all components of the cycle while their the compilation
        if (taskDescription != null)
          Log.log.log("Aborted because of detected cycle", Log.CORE);
        e.addCycleComponent(new BuildRequirement<>(depResult, buildReq));
        throw e;
      }
    } catch (RequiredBuilderFailed e) {
      BuilderResult required = e.getLastAddedBuilder();
      depResult.requires(required.result);
      depResult.setState(BuildUnit.State.FAILURE);

      e.addBuilder(builder, depResult);
      if (taskDescription != null)
        Log.log.logErr("Required builder failed", Log.CORE);
      throw e;
    } catch (Throwable e) {
      depResult.setState(BuildUnit.State.FAILURE);

      Log.log.logErr(e.getMessage(), Log.CORE);
      throw new RequiredBuilderFailed(builder, depResult, e);
    } finally {

      if (inputHash != DeepEquals.deepHashCode(builder.input))
        throw new AssertionError("API Violation detected: Builder mutated its input.");
      depResult.write();

      if (taskDescription != null)
        Log.log.endTask();
      this.consistentUnits.add(assertConsistency(depResult));
      BuildStackEntry poppedEntry = this.requireStack.pop();
      assert poppedEntry == entry : "Got the wrong build stack entry from the requires stack";
    }

    if (depResult.getState() == BuildUnit.State.FAILURE)
      throw new RequiredBuilderFailed(builder, depResult, new IllegalStateException("Builder failed for unknown reason, please confer log."));

    return depResult;
  }

  public <
  In extends Serializable, 
  Out extends BuildOutput, 
  B extends Builder<In, Out>, 
  F extends BuilderFactory<In, Out, B>
  > BuildUnit<Out> require(BuildRequest<In, Out, B, F> buildReq) throws IOException {
    if (rebuildTriggeredBy == null) {
      rebuildTriggeredBy = buildReq;
      Log.log.beginTask("Incrementally rebuild inconsistent units", Log.CORE);
    }

    try {
      Builder<In, Out> builder = buildReq.createBuilder();
      Path dep = builder.persistentPath();
      BuildUnit<Out> depResult = BuildUnit.read(dep, buildReq);

      if (depResult == null)
        return executeBuilder(builder, dep, buildReq);

      if (consistentUnits.contains(depResult))
        return depResult;

      if (!depResult.isConsistentNonrequirements())
        return executeBuilder(builder, dep, buildReq);

      for (Requirement req : depResult.getRequirements()) {
        if (req instanceof FileRequirement) {
          FileRequirement freq = (FileRequirement) req;
          if (!freq.isConsistent())
            return executeBuilder(builder, dep, buildReq);
        }
        else if (req instanceof BuildRequirement) {
          BuildRequirement<?> breq = (BuildRequirement<?>) req;
          require(breq.req);
        }
      }

      consistentUnits.add(assertConsistency(depResult));
      return depResult;
    } finally {
      if (rebuildTriggeredBy == buildReq)
        Log.log.endTask();
    }
  }

  private <Out extends BuildOutput> BuildUnit<Out> assertConsistency(BuildUnit<Out> depResult) {
    BuildUnit<?> other = generatedFiles.put(depResult.getPersistentPath(), depResult);
    if (other != null)
      throw new DuplicateBuildUnitPathException("Build unit " + depResult + " has same persistent path as build unit " + other);
    
    for (FileRequirement freq : depResult.getGeneratedFileRequirements()) {
      other = generatedFiles.put(freq.path, depResult);
      if (other != null)
        throw new DuplicateFileGenerationException("Build unit " + depResult + " generates same file as build unit " + other);
    }
    
//    if (!depResult.isConsistent(null))
//      throw new AssertionError("Build manager does not guarantee soundness");
    return depResult;
  }
}
