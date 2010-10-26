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
