/*
 * The MIT License
 * 
 * Copyright (c) 2010-2011, Jesse Bexten
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.ivy;

import java.util.HashSet;
import java.util.Set;

import com.rits.cloning.Cloner;

public class IvyClonerWrapper extends Cloner {

    private final Set<Class<?>> ignored = new HashSet<Class<?>>();
    
    @Override
    public void dontClone(Class<?>... c) {

        for (Class<?> cl : c)
        {
            this.ignored.add(cl);
        }
        super.dontClone(c);
    }

    @Override
    public <T> T deepClone(T o) {
        
        for (Class<?> clazz : ignored) 
        {
           if(clazz.isInstance(o))
           {
               return o;
           }
        }
        
        return super.deepClone(o);
    }

    
}
