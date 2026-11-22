package dev.booking.sports.shared.upload;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.exception.ApiException;

@Component
public class ObjectStorageRegistry {
    private final Map<StorageProvider, ObjectStorage> storages = new EnumMap<>(StorageProvider.class);
    private final StorageProvider uploadProvider;

    public ObjectStorageRegistry(List<ObjectStorage> adapters,
            @Value("${app.storage.provider:r2}") String uploadProvider) {
        this.uploadProvider = StorageProvider.valueOf(uploadProvider.trim().toUpperCase(Locale.ROOT));
        for (ObjectStorage adapter : adapters) {
            if (storages.putIfAbsent(adapter.provider(), adapter) != null)
                throw new IllegalStateException("Duplicate object storage provider: " + adapter.provider());
        }
        if (!storages.isEmpty() && !storages.containsKey(this.uploadProvider))
            throw new IllegalStateException("Selected upload storage provider is not enabled: " + uploadProvider);
    }

    public ObjectStorage forUploads() { return forProvider(uploadProvider); }

    public ObjectStorage forProvider(StorageProvider provider) {
        ObjectStorage storage = storages.get(provider);
        if (storage == null) throw new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE);
        return storage;
    }

    public Set<StorageProvider> enabledProviders() { return Set.copyOf(storages.keySet()); }
}
