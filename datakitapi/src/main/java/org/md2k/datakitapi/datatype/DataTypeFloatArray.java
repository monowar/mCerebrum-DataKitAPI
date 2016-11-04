package org.md2k.datakitapi.datatype;

import android.os.Parcel;
import android.os.Parcelable;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
public class DataTypeFloatArray extends  DataType implements Parcelable{
    public static final Creator<DataTypeFloatArray> CREATOR = new Creator<DataTypeFloatArray>() {
        @Override
        public DataTypeFloatArray createFromParcel(Parcel in) {
            return new DataTypeFloatArray(in);
        }

        @Override
        public DataTypeFloatArray[] newArray(int size) {
            return new DataTypeFloatArray[size];
        }
    };
    float[] sample;
    public DataTypeFloatArray(long timestamp, float[] sample) {
        super(timestamp);
        this.sample = new float[sample.length];
        System.arraycopy(sample, 0, this.sample, 0, sample.length);
    }

    public DataTypeFloatArray(DataTypeFloatArray dt) {
        super(dt);
        this.sample = new float[dt.sample.length];
        System.arraycopy(dt.sample, 0, this.sample, 0, sample.length);
    }

    public DataTypeFloatArray(){}

    protected DataTypeFloatArray(Parcel in) {
        super(in);
        sample = in.createFloatArray();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeFloatArray(sample);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public float[] getSample(){
        return sample;
    }
}
