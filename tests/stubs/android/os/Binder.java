package android.os;
public class Binder implements IBinder {
    public boolean transact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{return onTransact(code,data,reply,flags);}
    protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{return true;}
    public boolean isBinderAlive(){return true;}
    public String getInterfaceDescriptor(){return "test";}
}
