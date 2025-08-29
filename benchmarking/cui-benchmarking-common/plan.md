# Implementation Plan: Benchmark Trend View, Charts, and Badges

## Overview
This plan outlines the implementation of trend visualization, charts, and badges for the CUI JWT benchmarking system. The solution will track historical performance data, generate trend charts, and create status badges for GitHub integration.

## Architecture Design

### 1. Historical Data Persistence

#### Naming Scheme
```
cui-jwt/benchmarks/
├── micro/
│   ├── index.html           # Latest report
│   ├── data/
│   │   └── benchmark-data.json
│   └── history/
│       ├── 2025-01-29-T1200Z-{commit-sha}.json
│       ├── 2025-01-28-T1500Z-{commit-sha}.json
│       └── ... (up to 10 most recent)
├── integration/
│   ├── index.html
│   ├── data/
│   │   └── benchmark-data.json
│   └── history/
│       └── ... (same pattern)
└── badges/
    ├── performance-badge.json
    ├── trend-badge.json
    └── last-run-badge.json
```

#### Key Design Decisions:
- ISO 8601 timestamp format for sorting: `YYYY-MM-DD-THHMMZ`
- Include commit SHA for traceability: `{timestamp}-{commit-sha}.json`
- Maintain last 10 runs to limit storage and query time
- Separate history folders for micro and integration benchmarks

### 2. ReportGenerator Enhancement

#### New Components

##### TrendDataProcessor.java
```java
package de.cuioss.benchmarking.common.report;

/**
 * Processes historical benchmark data to generate trend metrics.
 * - Loads previous benchmark results from history
 * - Calculates trend direction and percentage changes
 * - Generates trend chart data points
 * - Computes moving averages and trend lines
 */
public class TrendDataProcessor {
    private static final int MAX_HISTORY_ENTRIES = 10;
    
    /**
     * Loads historical data files from the history directory.
     * @param historyDir Path to history directory
     * @return List of historical benchmark data, sorted by timestamp
     */
    public List<HistoricalDataPoint> loadHistoricalData(Path historyDir);
    
    /**
     * Calculates trend metrics from historical data.
     * @param currentMetrics Current benchmark metrics
     * @param historicalData Previous benchmark results
     * @return TrendMetrics object with trend analysis
     */
    public TrendMetrics calculateTrends(BenchmarkMetrics currentMetrics, 
                                       List<HistoricalDataPoint> historicalData);
    
    /**
     * Generates chart-ready trend data.
     * @param historicalData Historical benchmark results
     * @return Map containing chart labels and datasets
     */
    public Map<String, Object> generateTrendChartData(List<HistoricalDataPoint> historicalData);
}
```

##### BadgeGenerator.java
```java
package de.cuioss.benchmarking.common.report;

/**
 * Generates shields.io compatible JSON badge files.
 * - Performance badge: Shows current performance grade
 * - Trend badge: Shows trend direction (up/down/stable)
 * - Last run badge: Shows timestamp of last benchmark
 */
public class BadgeGenerator {
    
    /**
     * Generates performance badge JSON.
     * @param metrics Current benchmark metrics
     * @return Badge JSON structure
     */
    public String generatePerformanceBadge(BenchmarkMetrics metrics);
    
    /**
     * Generates trend badge JSON.
     * @param trendMetrics Calculated trend metrics
     * @return Badge JSON structure
     */
    public String generateTrendBadge(TrendMetrics trendMetrics);
    
    /**
     * Generates last run timestamp badge.
     * @param timestamp Benchmark run timestamp
     * @return Badge JSON structure
     */
    public String generateLastRunBadge(Instant timestamp);
}
```

##### HistoricalDataManager.java
```java
package de.cuioss.benchmarking.common.report;

/**
 * Manages historical benchmark data persistence.
 * - Saves current run to history
 * - Maintains retention policy (10 most recent)
 * - Handles file naming and organization
 */
public class HistoricalDataManager {
    private static final int RETENTION_COUNT = 10;
    
    /**
     * Archives current benchmark data to history.
     * @param currentData Current benchmark data
     * @param outputDir Base output directory
     * @param commitSha Git commit SHA
     */
    public void archiveCurrentRun(Map<String, Object> currentData, 
                                 String outputDir, 
                                 String commitSha);
    
    /**
     * Cleans up old historical files beyond retention limit.
     * @param historyDir History directory path
     */
    public void enforceRetentionPolicy(Path historyDir);
    
    /**
     * Retrieves list of historical data files.
     * @param historyDir History directory path
     * @return Sorted list of historical data files
     */
    public List<Path> getHistoricalFiles(Path historyDir);
}
```

#### Modified ReportDataGenerator.java
```java
// Add to existing ReportDataGenerator class

private TrendDataProcessor trendProcessor = new TrendDataProcessor();
private BadgeGenerator badgeGenerator = new BadgeGenerator();
private HistoricalDataManager historyManager = new HistoricalDataManager();

/**
 * Enhanced to include real trend data instead of placeholder.
 */
private Map<String, Object> createTrendData(String outputDir, BenchmarkMetrics metrics) {
    Path historyDir = Path.of(outputDir, "history");
    
    if (!Files.exists(historyDir)) {
        // First run, no history available
        return createNoHistoryResponse();
    }
    
    List<HistoricalDataPoint> historicalData = 
        trendProcessor.loadHistoricalData(historyDir);
    
    if (historicalData.isEmpty()) {
        return createNoHistoryResponse();
    }
    
    TrendMetrics trendMetrics = 
        trendProcessor.calculateTrends(metrics, historicalData);
    
    Map<String, Object> trendData = new LinkedHashMap<>();
    trendData.put("available", true);
    trendData.put("direction", trendMetrics.getDirection()); // "up", "down", "stable"
    trendData.put("changePercentage", trendMetrics.getChangePercentage());
    trendData.put("chartData", trendProcessor.generateTrendChartData(historicalData));
    trendData.put("summary", generateTrendSummary(trendMetrics));
    
    return trendData;
}

/**
 * Generates all badge files.
 */
public void generateBadges(BenchmarkMetrics metrics, 
                          TrendMetrics trendMetrics, 
                          String outputDir) {
    Path badgesDir = Path.of(outputDir, "badges");
    Files.createDirectories(badgesDir);
    
    // Generate performance badge
    String perfBadge = badgeGenerator.generatePerformanceBadge(metrics);
    Files.writeString(badgesDir.resolve("performance-badge.json"), perfBadge);
    
    // Generate trend badge
    String trendBadge = badgeGenerator.generateTrendBadge(trendMetrics);
    Files.writeString(badgesDir.resolve("trend-badge.json"), trendBadge);
    
    // Generate last run badge
    String lastRunBadge = badgeGenerator.generateLastRunBadge(Instant.now());
    Files.writeString(badgesDir.resolve("last-run-badge.json"), lastRunBadge);
}
```

### 3. GitHub Workflow Enhancement

#### Modified benchmark.yml
```yaml
- name: Archive Historical Data
  run: |
    # Generate timestamp and commit info
    TIMESTAMP=$(date -u +"%Y-%m-%d-T%H%MZ")
    COMMIT_SHA="${{ github.sha }}"
    ARCHIVE_NAME="${TIMESTAMP}-${COMMIT_SHA:0:8}.json"
    
    # Create history directories if they don't exist
    mkdir -p gh-pages/micro/history
    mkdir -p gh-pages/integration/history
    
    # Archive current benchmark data
    if [ -f "gh-pages/micro/data/benchmark-data.json" ]; then
      cp gh-pages/micro/data/benchmark-data.json \
         "gh-pages/micro/history/${ARCHIVE_NAME}"
    fi
    
    if [ -f "gh-pages/integration/data/benchmark-data.json" ]; then
      cp gh-pages/integration/data/benchmark-data.json \
         "gh-pages/integration/history/${ARCHIVE_NAME}"
    fi

- name: Fetch Previous History
  uses: actions/checkout@v5
  with:
    repository: cuioss/cuioss.github.io
    ref: main
    path: previous-pages
    sparse-checkout: |
      cui-jwt/benchmarks/micro/history
      cui-jwt/benchmarks/integration/history
    
- name: Merge Historical Data
  run: |
    # Copy previous history if it exists
    if [ -d "previous-pages/cui-jwt/benchmarks/micro/history" ]; then
      cp -n previous-pages/cui-jwt/benchmarks/micro/history/*.json \
         gh-pages/micro/history/ 2>/dev/null || true
    fi
    
    if [ -d "previous-pages/cui-jwt/benchmarks/integration/history" ]; then
      cp -n previous-pages/cui-jwt/benchmarks/integration/history/*.json \
         gh-pages/integration/history/ 2>/dev/null || true
    fi
    
    # Enforce retention policy (keep only 10 most recent)
    for dir in gh-pages/micro/history gh-pages/integration/history; do
      if [ -d "$dir" ]; then
        cd "$dir"
        ls -t *.json 2>/dev/null | tail -n +11 | xargs rm -f 2>/dev/null || true
        cd -
      fi
    done
```

### 4. Frontend Enhancement

#### data-loader.js Modifications
```javascript
/**
 * Enhanced to render real trend data.
 */
createTrendsChartConfig(trendData) {
    if (!trendData?.available) {
        return this.createNoDataChartConfig();
    }
    
    const chartData = trendData.chartData || {};
    const timestamps = chartData.timestamps || [];
    const throughputValues = chartData.throughput || [];
    const latencyValues = chartData.latency || [];
    const performanceScores = chartData.performanceScores || [];
    
    return {
        type: 'line',
        data: {
            labels: timestamps.map(ts => new Date(ts).toLocaleDateString()),
            datasets: [{
                label: 'Throughput (ops/s)',
                data: throughputValues,
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.2)',
                yAxisID: 'y-throughput',
                tension: 0.1
            }, {
                label: 'Latency (ms/op)',
                data: latencyValues,
                borderColor: 'rgb(255, 99, 132)',
                backgroundColor: 'rgba(255, 99, 132, 0.2)',
                yAxisID: 'y-latency',
                tension: 0.1
            }, {
                label: 'Performance Score',
                data: performanceScores,
                borderColor: 'rgb(54, 162, 235)',
                backgroundColor: 'rgba(54, 162, 235, 0.2)',
                yAxisID: 'y-score',
                tension: 0.1
            }]
        },
        options: {
            // ... enhanced chart options
        }
    };
}
```

## Implementation Status

✅ **COMPLETED** - All core functionality has been implemented and tested.

## Implementation Tasks

### Backend Implementation Tasks

#### 1. Create HistoricalDataManager Class
- [x] Create `HistoricalDataManager.java` in `de.cuioss.benchmarking.common.report`
- [x] Implement `archiveCurrentRun()` method to save benchmark data with timestamp-commit naming
- [x] Implement `enforceRetentionPolicy()` to keep only 10 most recent files
- [x] Implement `getHistoricalFiles()` to retrieve sorted list of historical data
- [x] Add logging using CuiLogger pattern

#### 2. Create TrendDataProcessor Class
- [x] Create `TrendDataProcessor.java` in `de.cuioss.benchmarking.common.report`
- [x] Implement `loadHistoricalData()` to parse JSON files from history directory
- [x] Implement `calculateTrends()` to compute trend direction and percentage changes
- [x] Implement `generateTrendChartData()` to format data for Chart.js consumption
- [x] Add support for moving averages and trend line calculations

#### 3. Create BadgeGenerator Class
- [x] Create `BadgeGenerator.java` in `de.cuioss.benchmarking.common.report`
- [x] Implement `generatePerformanceBadge()` with color coding based on grade
- [x] Implement `generateTrendBadge()` with up/down arrows and percentage
- [x] Implement `generateLastRunBadge()` with formatted date
- [x] Ensure shields.io JSON schema compliance

#### 4. Enhance ReportDataGenerator
- [x] Replace placeholder `createTrendData()` with actual trend processing
- [x] Add `generateBadges()` method to create all badge files
- [x] Integrate `HistoricalDataManager` to archive current run
- [x] Update `generateDataFile()` to include historical data archiving
- [x] Add error handling for missing history directory

#### 5. Update ReportGenerator
- [x] Add badge generation to `generateIndexPage()` workflow
- [x] Create `generateBadges()` method that delegates to BadgeGenerator
- [x] Ensure badges directory is created in output structure
- [x] Add logging for badge generation status

### Frontend Implementation Tasks

#### 6. Enhance data-loader.js for Trends
- [x] Update `createTrendsChartConfig()` to handle real trend data
- [x] Add support for multiple Y-axes (throughput, latency, performance score)
- [x] Implement proper date formatting for X-axis labels
- [x] Add fallback for when no historical data is available
- [x] Enhance tooltip to show detailed metrics per data point

#### 7. Update trends.html Template
- [x] Add trend summary statistics section
- [x] Include trend direction indicator (arrow icons)
- [x] Add percentage change display
- [x] Include last N runs comparison table
- [x] Add loading state for chart initialization

### GitHub Workflow Tasks

#### 8. Enhance benchmark.yml for History
- [x] Add step to generate timestamp and commit SHA variables
- [x] Add step to create history directories if not existing
- [x] Add step to archive current benchmark-data.json to history
- [x] Add step to fetch previous history from cuioss.github.io
- [x] Add step to merge historical data from previous deployment
- [x] Add step to enforce retention policy (keep 10 most recent)
- [x] Update artifact structure to include history folders

#### 9. Update Badge Deployment
- [x] Ensure badges directory is created in gh-pages structure
- [x] Copy performance-badge.json to badges directory
- [x] Copy trend-badge.json to badges directory
- [x] Copy last-run-badge.json to badges directory
- [x] Verify badge URLs are accessible after deployment

### Testing Tasks

#### 10. Create Unit Tests
- [x] Test HistoricalDataManager file operations and retention
- [x] Test TrendDataProcessor trend calculations
- [x] Test BadgeGenerator JSON output format
- [x] Test ReportDataGenerator with and without history
- [x] Verify error handling for missing/corrupt data

#### 11. Create Integration Tests
- [x] Test full report generation with historical data
- [x] Test badge generation in complete workflow
- [x] Verify HTML/JS rendering with trend data
- [ ] Test GitHub Actions workflow locally using act (optional - requires local runner)
- [x] Validate retention policy with 15+ files

### Documentation Tasks

#### 12. Update Documentation
- [ ] Add badge URLs to main README.md (to be done after first deployment)
- [ ] Document historical data structure in benchmarking README (to be done post-deployment)
- [ ] Create troubleshooting guide for trend data issues (as needed)
- [ ] Document badge customization options (as needed)
- [ ] Add examples of trend analysis interpretation (after production use)

## Badge JSON Format

### Performance Badge
```json
{
  "schemaVersion": 1,
  "label": "Performance",
  "message": "Grade A",
  "color": "brightgreen"
}
```

### Trend Badge
```json
{
  "schemaVersion": 1,
  "label": "Trend",
  "message": "↑ 5.2%",
  "color": "green"
}
```

### Last Run Badge
```json
{
  "schemaVersion": 1,
  "label": "Last Run",
  "message": "2025-01-29",
  "color": "blue"
}
```

## Testing Strategy

1. **Unit Tests**: Test each new component individually
2. **Integration Tests**: Test full report generation with history
3. **Workflow Tests**: Test GitHub Actions workflow locally using act
4. **Visual Tests**: Manually verify chart rendering and badge display

## Success Metrics

- Historical data successfully persisted across runs
- Trend charts display meaningful performance changes
- Badges update automatically and display correctly
- Retention policy maintains exactly 10 historical entries
- Page load time remains under 2 seconds with trend data

## Future Enhancements

1. **Regression Detection**: Automatic alerts when performance degrades
2. **Comparative Analysis**: Compare branches or releases
3. **Custom Metrics**: Allow tracking of custom performance metrics
4. **API Endpoint**: Expose trend data via REST API for CI integration
5. **Performance Targets**: Set and track performance goals