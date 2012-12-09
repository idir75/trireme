package com.apigee.noderunner.core.modules;

import com.apigee.noderunner.core.NodeModule;
import com.apigee.noderunner.core.internal.Charsets;
import com.apigee.noderunner.core.internal.NodeExitException;
import com.apigee.noderunner.core.internal.ScriptRunner;
import com.apigee.noderunner.core.internal.Utils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * The Node 0.8.15 Process object done on top of the VM.
 */
public class Process
    implements NodeModule
{
    // TODO
    public static final String NODERUNNER_VERSION = "0.1";

    protected final static String CLASS_NAME = "_processClass";
    protected final static String OBJECT_NAME = "process";

    @Override
    public String getModuleName() {
        return "process";
    }

    @Override
    public Object registerExports(Context cx, Scriptable scope, ScriptRunner runner)
        throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
        ScriptableObject.defineClass(scope, ProcessImpl.class, false, true);
        ProcessImpl exports = (ProcessImpl)cx.newObject(scope, CLASS_NAME);

        ScriptableObject.defineClass(scope, SimpleOutputStreamImpl.class);
        SimpleOutputStreamImpl stdout = (SimpleOutputStreamImpl)cx.newObject(scope, SimpleOutputStreamImpl.CLASS_NAME);
        stdout.setOutput(System.out);
        exports.setStdout(stdout);
        SimpleOutputStreamImpl stderr = (SimpleOutputStreamImpl)cx.newObject(scope, SimpleOutputStreamImpl.CLASS_NAME);
        stderr.setOutput(System.err);
        exports.setStderr(stderr);
        scope.put(OBJECT_NAME, scope, exports);
        return exports;
    }

    public static class ProcessImpl
        extends EventEmitter.EventEmitterImpl
    {
        private Object stdout;
        private Object stderr;
        private long startTime;
        private ScriptRunner runner;

        @JSConstructor
        public ProcessImpl()
        {
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public String getClassName() {
            return CLASS_NAME;
        }

        public void setRunner(ScriptRunner runner) {
            this.runner = runner;
        }

        // TODO stdin

        @JSGetter("stdout")
        public Object getStdout() {
            return stdout;
        }

        public void setStdout(Object stdout) {
            this.stdout = stdout;
        }

        @JSGetter("stderr")
        public Object getStderr() {
            return stderr;
        }

        public void setStderr(Object stderr) {
            this.stderr = stderr;
        }

        @JSGetter("argv")
        public Object getArgv()
        {
            return null;
            /*
            ProcessImpl p = (ProcessImpl)thisObj;
            String[] ret = new String[p.argv.length];
            System.arraycopy(p.argv, 1, ret, 1, p.argv.length - 1);
            ret[0] = "node";
            return Context.javaToJS(ret, thisObj);
            */
        }

        @JSGetter("execPath")
        public String getExecPath()
        {
            // TODO ??
            return "./node";
        }

        @JSFunction
        public void abort()
            throws NodeExitException
        {
            throw new NodeExitException(true, 0);
        }

        @JSFunction
        public static Object exit(Context cx, Scriptable thisObj, Object[] args, Function func)
            throws NodeExitException
        {
            if (args.length >= 1) {
                int code = (Integer)Context.jsToJava(args[0], Integer.class);
                throw new NodeExitException(false, code);
            } else {
                throw new NodeExitException(false, 0);
            }
        }

        // TODO chdir
        // TODO cwd
        // TODO getgid
        // TODO setgid
        // TODO getuid
        // TODO setuid

        @JSFunction
        public static Object getenv(Context cx, Scriptable thisObj, Object[] args, Function func)
        {
            Map<String, String> envMap = System.getenv();
            Scriptable env = cx.newObject(thisObj);

            for (Map.Entry<String, String> e : envMap.entrySet()) {
                env.put(e.getKey(), thisObj, e.getValue());
            }
            return env;
        }

        @JSGetter("version")
        public String getVersion()
        {
            return NODERUNNER_VERSION;
        }

        @JSFunction
        public static Object versions(Context cx, Scriptable thisObj, Object[] args, Function func)
        {
            Scriptable env = cx.newObject(thisObj);
            env.put("noderunner", thisObj, NODERUNNER_VERSION);
            return env;
        }

        // TODO config
        // TODO kill
        // TODO pid
        // TODO title
        // TODO arch
        // TODO umask

        @JSGetter("platform")
        public String getPlatform()
        {
            return "java";
        }

        @JSFunction
        public static Object memoryUsage(Context cx, Scriptable thisObj, Object[] args, Function func)
        {
            Scriptable mem = cx.newObject(thisObj);
            mem.put("heapTotal", thisObj, Runtime.getRuntime().maxMemory());
            mem.put("heapUsed", thisObj,  Runtime.getRuntime().totalMemory());
            return mem;
        }

        @JSFunction
        public void nextTick(Function f)
        {
            runner.addTickFunction(f);
        }

        @JSFunction
        public long uptime()
        {
            long up = (System.currentTimeMillis() - startTime) / 1000L;
            return up;
        }

        @JSFunction
        public long hrtime()
        {
            return System.nanoTime();
        }
    }

    /**
     * In theory there are lots of evented I/O things that we are supposed to do with stdout and
     * stderr, but that is a lot of complication.
     */
    public static class SimpleOutputStreamImpl
        extends ScriptableObject
    {
        protected static final String CLASS_NAME = "_outStreamClass";

        private OutputStream out;

        @Override
        public String getClassName() {
            return CLASS_NAME;
        }

        public void setOutput(OutputStream out) {
            this.out = out;
        }

        @JSFunction
        public static boolean write(Context ctx, Scriptable thisObj, Object[] args, Function caller)
        {
            if (args.length == 0) {
                return true;
            }

            String s = (String)Context.jsToJava(args[0], String.class);
            String enc = null;
            if (args.length > 1) {
                enc = (String)Context.jsToJava(args[1], String.class);
            }
            Charset charset = Charsets.get().resolveCharset(enc);

            SimpleOutputStreamImpl os = (SimpleOutputStreamImpl)thisObj;
            try {
                os.out.write(s.getBytes(charset));
            } catch (IOException e) {
                throw new WrappedException(e);
            }
            return true;
        }

        @JSGetter("writable")
        public boolean isWriteable() {
            return true;
        }
    }
}
