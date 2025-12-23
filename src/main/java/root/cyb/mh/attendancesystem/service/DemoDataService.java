package root.cyb.mh.attendancesystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import root.cyb.mh.attendancesystem.model.*;
import root.cyb.mh.attendancesystem.repository.*;

import java.time.*;
import java.util.*;

@Service
public class DemoDataService {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private AttendanceLogRepository attendanceRepository;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private PayrollService payrollService;

    // New Repositories
    @Autowired
    private ShiftRepository shiftRepository;
    @Autowired
    private EmployeeShiftRepository employeeShiftRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    // Constants for Demo Data
    private static final String DEMO_PREFIX = "demo_";

    public void seedDemoData() {
        // 1. Create Departments if not exists
        Department eng = createDept("Development");
        Department sales = createDept("Sales");
        Department marketing = createDept("Marketing");

        // 2. Create Shifts
        Shift morningShift = createShift("Morning Shift", LocalTime.of(6, 0), LocalTime.of(15, 0));
        Shift nightShift = createShift("Night Shift", LocalTime.of(22, 0), LocalTime.of(7, 0));

        // 3. Create Personas
        createPersona("Iron Man", "Tony Stark", "demo_tony", eng, 5000.0, "PERFECT", null);
        createPersona("The Latecomer", "Peter Parker", "demo_peter", sales, 3000.0, "LATE", null);
        createPersona("The Ghost", "Natasha Romanoff", "demo_nat", marketing, 4500.0, "ABSENT", null);

        // 4. Advanced Personas
        Employee nightOwl = createPersona("Night Owl", "Bruce Wayne", "demo_bruce", eng, 8000.0, "PERFECT", nightShift);
        Employee sickOne = createPersona("The Sick One", "Steve Rogers", "demo_steve", sales, 4000.0, "SICK_LEAVE",
                null);

        // 5. Inject Leave Requests for 'The Sick One'
        if (sickOne != null) {
            LocalDate today = LocalDate.now();
            // Approved Sick Leave last month
            createLeave(sickOne, today.minusMonths(1).withDayOfMonth(5), today.minusMonths(1).withDayOfMonth(7), "Sick",
                    LeaveRequest.Status.APPROVED);
            // Pending Casual Leave last week
            createLeave(sickOne, today.minusDays(7), today.minusDays(7), "Casual", LeaveRequest.Status.PENDING);
            // Rejected Emergency yesterday
            createLeave(sickOne, today.minusDays(1), today.minusDays(1), "Emergency", LeaveRequest.Status.REJECTED);
        }

        System.out.println("Demo Data Injection Complete.");
    }

    public void clearDemoData() {
        // Delete all data associated with demo users
        List<Employee> demoEmps = employeeRepository.findAll().stream()
                .filter(e -> e.getId().startsWith(DEMO_PREFIX))
                .toList();

        for (Employee emp : demoEmps) {
            // Delete related Attendance
            List<AttendanceLog> attendances = attendanceRepository.findByEmployeeId(emp.getId());
            attendanceRepository.deleteAll(attendances);

            // Delete related Payslips
            List<Payslip> payslips = payslipRepository.findByEmployeeIdOrderByMonthDesc(emp.getId());
            payslipRepository.deleteAll(payslips);

            // Delete related Shifts
            List<EmployeeShift> shifts = employeeShiftRepository.findByEmployeeId(emp.getId());
            employeeShiftRepository.deleteAll(shifts);

            // Delete related Logs/Leaves (Need to check if mapped by ID)
            // LeaveRequests have a ManyToOne Employee.
            // We can't easily findByEmployeeId unless we iterate or add method.
            // But since strict FK constraints might default to cascade or restrict, we
            // should clean up if possible.
            // Assuming JPA Cascade might handle it if configured, otherwise simple iterate:
            List<LeaveRequest> leaves = leaveRequestRepository.findAll().stream()
                    .filter(l -> l.getEmployee().getId().equals(emp.getId()))
                    .toList();
            leaveRequestRepository.deleteAll(leaves);

            // Delete Employee
            employeeRepository.delete(emp);
        }
        System.out.println("Demo Data Cleared.");
    }

    private Department createDept(String name) {
        return departmentRepository.findByName(name)
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setName(name);
                    return departmentRepository.save(d);
                });
    }

    private Shift createShift(String name, LocalTime start, LocalTime end) {
        return shiftRepository.findByName(name)
                .orElseGet(() -> {
                    Shift s = new Shift();
                    s.setName(name);
                    s.setStartTime(start);
                    s.setEndTime(end);
                    return shiftRepository.save(s);
                });
    }

    private Employee createPersona(String role, String name, String id, Department dept, Double salary, String behavior,
            Shift shift) {
        // Return existing if present to avoid re-creation logic issues
        if (employeeRepository.existsById(id))
            return employeeRepository.findById(id).orElse(null);

        Employee emp = new Employee();
        emp.setId(id);
        emp.setName(name);
        emp.setDepartment(dept);
        emp.setMonthlySalary(salary);
        emp.setJoiningDate(LocalDate.now().minusMonths(8)); // Joined 8 months ago
        emp = employeeRepository.save(emp);

        // Assign Shift if provided (for last 6 months)
        if (shift != null) {
            EmployeeShift es = new EmployeeShift();
            es.setEmployee(emp);
            es.setShift(shift);
            es.setStartDate(LocalDate.now().minusMonths(8));
            es.setEndDate(LocalDate.now().plusMonths(12)); // Valid for a year more
            employeeShiftRepository.save(es);
        }

        // Generate 6 Months History
        LocalDate start = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        LocalDate end = LocalDate.now();

        generateAttendanceHistory(emp, start, end, behavior);
        generatePayrollHistory(emp, start, end);

        return emp;
    }

    private void createLeave(Employee emp, LocalDate start, LocalDate end, String type, LeaveRequest.Status status) {
        LeaveRequest le = new LeaveRequest();
        le.setEmployee(emp);
        le.setStartDate(start);
        le.setEndDate(end);
        le.setLeaveType(type);
        le.setStatus(status);
        if (status == LeaveRequest.Status.REJECTED)
            le.setAdminComment("Demo Rejection");
        leaveRequestRepository.save(le);
    }

    private void generateAttendanceHistory(Employee emp, LocalDate start, LocalDate end, String behavior) {
        // Default Times
        LocalTime defaultStart = LocalTime.of(9, 0);
        LocalTime defaultEnd = LocalTime.of(18, 0);

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            // Check for explicit Shift
            LocalTime workStart = defaultStart;
            LocalTime workEnd = defaultEnd;

            Optional<EmployeeShift> esOpt = employeeShiftRepository.findActiveShiftForEmployee(emp.getId(), date);
            if (esOpt.isPresent()) {
                Shift s = esOpt.get().getShift();
                if (s.getStartTime() != null)
                    workStart = s.getStartTime();
                if (s.getEndTime() != null)
                    workEnd = s.getEndTime();
            }

            // Skip weekends (Simple logic: Sat/Sun).
            // Note: Night shifts might cross days, but for demo simplistic logic: check
            // start day.
            if (date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY)
                continue;

            // Apply Behavior
            boolean isPresent = true;
            LocalTime inTime = workStart;
            LocalTime outTime = workEnd;

            if (behavior.equals("PERFECT")) {
                // Variation: +/- 5 mins
                inTime = workStart.minusMinutes((long) (Math.random() * 5));
                outTime = workEnd.plusMinutes((long) (Math.random() * 10));
            } else if (behavior.equals("LATE")) {
                // 50% chance of being late
                if (Math.random() > 0.5) {
                    inTime = workStart.plusMinutes(20 + (long) (Math.random() * 40));
                }
            } else if (behavior.equals("ABSENT")) {
                // 20% chance of absent
                if (Math.random() < 0.2) {
                    isPresent = false;
                }
            } else if (behavior.equals("SICK_LEAVE")) {
                // 10% chance random absent (unrecorded leave)
                if (Math.random() < 0.1) {
                    isPresent = false;
                }
            }

            if (isPresent) {
                // Handle Night Shift Crossover (End time is next day)
                LocalDateTime checkInDT = LocalDateTime.of(date, inTime);
                LocalDateTime checkOutDT;

                if (outTime.isBefore(inTime)) {
                    // Ends next day
                    checkOutDT = LocalDateTime.of(date.plusDays(1), outTime);
                } else {
                    checkOutDT = LocalDateTime.of(date, outTime);
                }

                // Create Check In Log
                AttendanceLog checkIn = new AttendanceLog();
                checkIn.setEmployeeId(emp.getId());
                checkIn.setTimestamp(checkInDT);
                checkIn.setDeviceId(1L);
                attendanceRepository.save(checkIn);

                // Create Check Out Log
                AttendanceLog checkOut = new AttendanceLog();
                checkOut.setEmployeeId(emp.getId());
                checkOut.setTimestamp(checkOutDT);
                checkOut.setDeviceId(1L);
                attendanceRepository.save(checkOut);
            }
        }
    }

    private void generatePayrollHistory(Employee emp, LocalDate start, LocalDate end) {
        YearMonth current = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);

        while (current.isBefore(last)) { // Don't generate for current incomplete month
            try {
                // Use existing Payroll Service to calculate accurate net salary based on the
                // injected attendance
                payrollService.createPayslipForEmployee(emp, current);

                // Mark as PAID for realism
                Optional<Payslip> slipOpt = payslipRepository.findByEmployeeIdAndMonth(emp.getId(), current.toString());
                if (slipOpt.isPresent()) {
                    Payslip slip = slipOpt.get();
                    slip.setStatus(Payslip.Status.PAID);
                    payslipRepository.save(slip);
                }
            } catch (Exception e) {
                System.err.println("Failed to gen payslip for " + emp.getName() + ": " + e.getMessage());
            }
            current = current.plusMonths(1);
        }
    }
}
