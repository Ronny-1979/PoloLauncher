package de.ronny.pololauncher;
import android.content.*;
import android.os.*;
public final class CanBindingTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var field=HctCanbusBinderRuntime.class.getDeclaredField("CONNECTION");field.setAccessible(true);
        ServiceConnection connection=(ServiceConnection)field.get(null);
        ComponentName name=new ComponentName("test","Service");
        Context context=new Context();
        HctCanbusBinderRuntime.acquire(context);check(context.binds==1,"First bind requested");
        connection.onServiceConnected(name,new Binder());
        connection.onServiceDisconnected(name);
        HctCanbusBinderRuntime.ensureBound(context);
        check(context.binds==1 && context.unbinds==0,"Disconnected binding retained, no duplicate bind");
        HctCanbusBinderRuntime.release(context);check(context.unbinds==1,"Disconnected binding released");
        HctCanbusBinderRuntime.acquire(context);connection.onBindingDied(name);
        check(context.unbinds==2,"Dead binding released");
        HctCanbusBinderRuntime.ensureBound(context);check(context.binds==2,"Retry cooldown observed");
        SystemClock.now+=5001;HctCanbusBinderRuntime.ensureBound(context);
        check(context.binds==3,"Rebind after cooldown");
        connection.onNullBinding(name);check(context.unbinds==3,"Null binding released");
        HctCanbusBinderRuntime.release(context);check(context.unbinds==3,"No double unbind");
        HctCanbusBinderRuntime.ensureBound(context);check(context.binds==3,"No new binding without owner");
        System.out.println("CanBindingTest: all cases passed");
    }
}
