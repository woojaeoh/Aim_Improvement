package capstone25_2.aim.service;

import capstone25_2.aim.domain.Analyst;
import capstone25_2.aim.repository.AnalystRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalystService {
    private final AnalystRepository analystRepository;

    public List<Analyst> getAnalystsByFirm(String firmName) {
        return analystRepository.findByFirmName(firmName);
    }

    public Optional<Analyst> getAnalystById(Long id) {
        return analystRepository.findById(id);
    }

}
