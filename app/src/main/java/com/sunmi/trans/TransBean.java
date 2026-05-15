package com.sunmi.trans;

import android.os.Parcel;
import android.os.Parcelable;

public class TransBean implements Parcelable {

    public static final Creator<TransBean> CREATOR = new Creator<TransBean>() {
        @Override
        public TransBean createFromParcel(Parcel in) {
            return new TransBean();
        }

        @Override
        public TransBean[] newArray(int size) {
            return new TransBean[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }
}
