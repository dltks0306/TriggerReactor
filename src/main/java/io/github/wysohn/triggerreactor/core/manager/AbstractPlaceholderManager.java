package io.github.wysohn.triggerreactor.core.manager;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import io.github.wysohn.triggerreactor.core.main.TriggerReactor;
import io.github.wysohn.triggerreactor.core.script.interpreter.Placeholder;
import io.github.wysohn.triggerreactor.tools.ReflectionUtil;

public abstract class AbstractPlaceholderManager extends AbstractJavascriptBasedManager implements KeyValueManager<Placeholder>  {
    protected Map<String, Placeholder> jsPlaceholders = new HashMap<>();

    public AbstractPlaceholderManager(TriggerReactor plugin) throws ScriptException {
        super(plugin);
    }

    protected void reloadPlaceholders(File file, FileFilter filter) throws ScriptException, IOException{
        String fileName = file.getName();
        fileName = fileName.substring(0, fileName.indexOf("."));

        if(jsPlaceholders.containsKey(fileName)){
            plugin.getLogger().warning(fileName+" already registered! Duplicating placerholders?");
        }else{
            JSPlaceholder placeholder = new JSPlaceholder(fileName, file);
            jsPlaceholders.put(fileName, placeholder);
        }
    }
    @Override
    public Placeholder get(Object key) {
        return jsPlaceholders.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return jsPlaceholders.containsKey(key);
    }

    @Override
    public Set<Entry<String, Placeholder>> entrySet() {
        Set<Entry<String, Placeholder>> set = new HashSet<>();
        for(Entry<String, Placeholder> entry : jsPlaceholders.entrySet()){
            set.add(new AbstractMap.SimpleEntry<String, Placeholder>(entry.getKey(), entry.getValue()));
        }
        return set;
    }

    @Override
    public Map<String, Placeholder> getBackedMap() {
        return jsPlaceholders;
    }

    public static class JSPlaceholder extends Placeholder{
        private final String placeholderName;
        private final String sourceCode;

        private ScriptEngine engine = getNashornEngine();
        private CompiledScript compiled = null;

        public JSPlaceholder(String placeholderName, File file) throws ScriptException, IOException {
            this.placeholderName = placeholderName;

            StringBuilder builder = new StringBuilder();
            FileReader reader = new FileReader(file);
            int read = -1;
            while((read = reader.read()) != -1)
                builder.append((char) read);
            reader.close();
            sourceCode = builder.toString();

            Compilable compiler = (Compilable) engine;
            compiled = compiler.compile(sourceCode);
        }

        @Override
        public Object parse(Object context, Object... args) throws Exception {
            ///////////////////////////////
            Map<String, Object> variables = new HashMap<>();
            Map<String, Object> vars = ReflectionUtil.extractVariables(context);
            variables.putAll(vars);

            instance.extractCustomVariables(variables, context);
            ///////////////////////////////

            ScriptContext scriptContext = engine.getContext();
            final Bindings bindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);
            for(Map.Entry<String, Object> entry : variables.entrySet()){
                String key = entry.getKey();
                Object value = entry.getValue();
                bindings.put(key, value);
            }

            try {
                compiled.eval(scriptContext);
            } catch (ScriptException e2) {
                e2.printStackTrace();
            }

            Invocable invocable = (Invocable) compiled.getEngine();
            Callable<Integer> call = new Callable<Integer>(){
                @Override
                public Integer call() throws Exception {
                    Object argObj = args;

                    if(TriggerReactor.getInstance().isDebugging()){
                        Integer result = null;
                        long start = System.currentTimeMillis();
                        result = (Integer) invocable.invokeFunction(placeholderName, argObj);
                        long end = System.currentTimeMillis();
                        TriggerReactor.getInstance().getLogger().info(placeholderName+" placeholder -- "+(end - start)+"ms");
                        return result;
                    }else{
                        return (Integer) invocable.invokeFunction(placeholderName, argObj);
                    }
                }
            };

            if(TriggerReactor.getInstance().isServerThread()){
                Integer result = null;
                try {
                    result = call.call();
                } catch (Exception e1) {
                    e1.printStackTrace();
                    throw new Exception("$"+placeholderName+" encountered error.", e1);
                }
                return result;
            }else{
                Future<Integer> future = runSyncTaskForFuture(call);

                Integer result = null;
                try {
                    result = future.get(5, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException e1) {
                    throw new Exception("$"+placeholderName+" encountered error.", e1);
                } catch (TimeoutException e1) {
                    throw new Exception("$"+placeholderName+" was stopped. It took longer than 5 seconds to process. Is the server lagging?", e1);
                }
                return result;
            }
        }
    }
}
