package com.renttool.application.identity.port.out;

import com.renttool.domain.identity.ProviderProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository {
  Optional<ProviderProfile> findByAccountId(Long accountId);

  List<ProviderProfile> findAllByAccountId(Collection<Long> accountIds);

  Optional<ProviderProfile> findByNit(String nit);

  ProviderProfile save(ProviderProfile profile);
}
