package root.cyb.mh.attendancesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Value("${app.company.name:Attendance System}")
    private String companyName;

    @ModelAttribute("companyName")
    public String companyName() {
        return companyName;
    }
}
