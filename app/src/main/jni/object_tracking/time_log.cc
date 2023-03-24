

#include "object_tracking/time_log.h"

#ifdef LOG_TIME
// Storage for logging functionality.
int num_time_logs = 0;
LogEntry time_logs[NUM_LOGS];

int num_avg_entries = 0;
AverageEntry avg_entries[NUM_LOGS];
#endif
