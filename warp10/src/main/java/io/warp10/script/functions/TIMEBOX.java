//
//   Copyright 2018-2021  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.functions;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.warp10.WarpConfig;
import io.warp10.continuum.Configuration;
import io.warp10.continuum.store.Constants;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStack.Macro;
import io.warp10.script.WarpScriptStackFunction;

public class TIMEBOX extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  /**
   * Default timeboxing is 30s
   */
  private static final long DEFAULT_TIMEBOX_MAXTIME = 30000L;
  
  /**
   * Maximum timeboxing possible
   */
  private final long TIMEBOX_MAXTIME;

  /**
   * Wrapped function
   */
  private final WarpScriptStackFunction function;

  public TIMEBOX(String name, WarpScriptStackFunction function) {
    this(name, function, DEFAULT_TIMEBOX_MAXTIME);
  }

  public TIMEBOX(String name, WarpScriptStackFunction function, long maxtime) {
    super(name);
    this.function = function;
    TIMEBOX_MAXTIME = maxtime;
  }

  public TIMEBOX(String name, WarpScriptStackFunction function, String configMaxtimeKey) {
    super(name);
    this.function = function;
    TIMEBOX_MAXTIME = Long.parseLong(WarpConfig.getProperty(configMaxtimeKey, Long.toString(DEFAULT_TIMEBOX_MAXTIME)));
  }

  /**
   * No wrapped function: TIMEBOX will expect a macro and a maxtime long on top of the stack.
   */
  public TIMEBOX(String name) {
    this(name, null, Configuration.CONFIG_WARPSCRIPT_TIMEBOX_MAXTIME);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = null;
    long maxtime = TIMEBOX_MAXTIME;

    if (null == function) {
      top = stack.pop();

      if (!(top instanceof Long)) {
        throw new WarpScriptException(getName() + " expects a maximum execution time on top of the stack.");
      }

      maxtime = Math.min(Math.max(0L, ((Number) top).longValue() / Constants.TIME_UNITS_PER_MS), maxtime);

      top = stack.pop();

      if (!(top instanceof Macro)) {
        throw new WarpScriptException(getName() + " operates on a macro.");
      }
    }

    final Macro macro = (Macro) top;
    final WarpScriptStack fstack = stack;
    
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<Object> future = executorService.submit(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        if (null == macro) {
          function.apply(fstack);
        } else {
          fstack.exec(macro);
        }
        return fstack;
      }
    });
    
    try {
      future.get(maxtime, TimeUnit.MILLISECONDS);      
    } catch (TimeoutException te) {
      throw new WarpScriptException(getName() + " reached the execution time limit (" + maxtime + " ms).");
    } catch (ExecutionException ee) {
      throw new WarpScriptException(getName() + " encountered an exception while executing macro", ee.getCause());
    } catch (Exception e) {
      throw new WarpScriptException(getName() + " encountered an exception", e);
    } finally {
      executorService.shutdown();
      executorService.shutdownNow();
      if (!executorService.isShutdown()) {
        throw new WarpScriptException(getName() + " could not be properly shut down.");
      }
    }

    return stack;
  }
}
