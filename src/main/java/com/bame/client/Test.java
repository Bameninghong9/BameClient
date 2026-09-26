import net.minecraft.client.network.AbstractClientPlayerEntity; 
public class Test { 
    public static void main(String[] args) { 
        for (java.lang.reflect.Method m : AbstractClientPlayerEntity.class.getMethods()) { 
            if (m.getName().toLowerCase().contains("skin")) 
                System.out.println(m.getName()); 
        } 
    } 
}
