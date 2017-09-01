package tetzlaff.util;

import tetzlaff.gl.exceptions.NoAvailableTextureUnitsException;

public class ResourceManager<ResourceType>
{
    public final int length;

    private final int[] keys;
    private final Object[] resources;
    private int nextSlot;

    public ResourceManager(int length)
    {
        this.length = length;
        keys = new int[length];
        for (int i = 0; i < length; i++)
        {
            keys[i] = -1;
        }
        resources = new Object[length];
        nextSlot = 0;
    }

    public int assignResourceByKey(int key, ResourceType resource)
    {
        // Check if the key has already been assigned a texture
        for (int i = 0; i < length; i++)
        {
            if (keys[i] == key)
            {
                resources[i] = resource;
                return i;
            }
        }

        if (nextSlot == length)
        {
            // No more slots available.
            throw new NoAvailableTextureUnitsException("No more available resource slots.");
        }
        else
        {
            // The key has not been assigned a resource, so use the next available slot
            keys[nextSlot] = key;
            resources[nextSlot] = resource;
            int result = nextSlot;
            nextSlot++;
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public ResourceType getResourceByUnit(int index)
    {
        return (ResourceType)resources[index];
    }
}
