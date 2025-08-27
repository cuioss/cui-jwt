/**
 * Data loader for benchmark report pages.
 * Loads and provides access to benchmark-data.json
 */
class BenchmarkDataLoader {
    constructor() {
        this.data = null;
        this.loadPromise = null;
    }

    /**
     * Loads the benchmark data JSON file.
     * @returns {Promise<Object>} Promise resolving to the data object
     */
    async loadData() {
        if (this.data) {
            return this.data;
        }

        if (!this.loadPromise) {
            this.loadPromise = fetch('benchmark-data.json')
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Failed to load benchmark data: ${response.statusText}`);
                    }
                    return response.json();
                })
                .then(data => {
                    this.data = data;
                    return data;
                })
                .catch(error => {
                    console.error('Error loading benchmark data:', error);
                    // Return empty structure on error
                    return {
                        metadata: {},
                        overview: {},
                        benchmarks: [],
                        chartData: {},
                        trends: {}
                    };
                });
        }

        return this.loadPromise;
    }

    /**
     * Renders the overview section with data from JSON.
     */
    async renderOverview() {
        const data = await this.loadData();
        const overview = data.overview || {};
        
        // Update overview elements
        const elements = {
            'total-benchmarks': overview.totalBenchmarks || 0,
            'avg-throughput': overview.avgThroughputFormatted || 'N/A',
            'avg-latency': overview.avgLatencyFormatted || 'N/A',
            'performance-grade': overview.performanceGrade || 'N/A'
        };

        for (const [id, value] of Object.entries(elements)) {
            const element = document.getElementById(id);
            if (element) {
                element.textContent = value;
            }
        }

        // Update timestamp if present
        const timestampElement = document.getElementById('report-timestamp');
        if (timestampElement && data.metadata?.displayTimestamp) {
            timestampElement.textContent = data.metadata.displayTimestamp;
        }
    }

    /**
     * Renders the benchmark table with data from JSON.
     */
    async renderBenchmarkTable() {
        const data = await this.loadData();
        const benchmarks = data.benchmarks || [];
        const tableBody = document.getElementById('benchmark-table-body');
        
        if (!tableBody) return;

        // Clear existing rows
        tableBody.innerHTML = '';

        // Add benchmark rows
        benchmarks.forEach(benchmark => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${benchmark.name}</td>
                <td>${benchmark.mode}</td>
                <td>${benchmark.scoreFormatted}</td>
                <td class="confidence-cell">
                    ${benchmark.confidence ? 
                        `[${benchmark.confidence[0].toFixed(2)}, ${benchmark.confidence[1].toFixed(2)}]` : 
                        'N/A'}
                </td>
            `;
            tableBody.appendChild(row);
        });
    }

    /**
     * Renders charts using the chart data from JSON.
     * @param {string} canvasId - The ID of the canvas element
     * @param {string} chartType - Type of chart to render ('overview', 'trends', 'detailed')
     */
    async renderChart(canvasId, chartType = 'overview') {
        const data = await this.loadData();
        const chartData = data.chartData || {};
        const canvas = document.getElementById(canvasId);
        
        if (!canvas || !window.Chart) return;

        const ctx = canvas.getContext('2d');
        
        let config;
        switch (chartType) {
            case 'overview':
                config = this.createOverviewChartConfig(chartData);
                break;
            case 'trends':
                config = this.createTrendsChartConfig(data.trends);
                break;
            case 'detailed':
                config = this.createDetailedChartConfig(chartData);
                break;
            default:
                config = this.createOverviewChartConfig(chartData);
        }

        new Chart(ctx, config);
    }

    /**
     * Creates configuration for the overview chart.
     */
    createOverviewChartConfig(chartData) {
        return {
            type: 'bar',
            data: {
                labels: chartData.labels || [],
                datasets: [{
                    label: 'Throughput (ops/s)',
                    data: chartData.throughput || [],
                    backgroundColor: 'rgba(75, 192, 192, 0.6)',
                    borderColor: 'rgba(75, 192, 192, 1)',
                    borderWidth: 1,
                    yAxisID: 'y-throughput'
                }, {
                    label: 'Latency (ms/op)',
                    data: chartData.latency || [],
                    backgroundColor: 'rgba(255, 99, 132, 0.6)',
                    borderColor: 'rgba(255, 99, 132, 1)',
                    borderWidth: 1,
                    yAxisID: 'y-latency'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'Benchmark Performance Overview'
                    },
                    legend: {
                        display: true,
                        position: 'top'
                    }
                },
                scales: {
                    'y-throughput': {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        title: {
                            display: true,
                            text: 'Throughput (ops/s)'
                        }
                    },
                    'y-latency': {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        title: {
                            display: true,
                            text: 'Latency (ms/op)'
                        },
                        grid: {
                            drawOnChartArea: false
                        }
                    }
                }
            }
        };
    }

    /**
     * Creates configuration for the trends chart.
     */
    createTrendsChartConfig(trendData) {
        const runs = trendData?.runs || [];
        const labels = runs.map((_, index) => `Run ${index + 1}`);
        const throughputData = runs.map(run => run.avgThroughput || 0);
        const latencyData = runs.map(run => run.avgLatency || 0);

        return {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Average Throughput (ops/s)',
                    data: throughputData,
                    borderColor: 'rgb(75, 192, 192)',
                    backgroundColor: 'rgba(75, 192, 192, 0.2)',
                    tension: 0.1
                }, {
                    label: 'Average Latency (ms/op)',
                    data: latencyData,
                    borderColor: 'rgb(255, 99, 132)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    tension: 0.1,
                    yAxisID: 'y2'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'Performance Trends Over Time'
                    }
                },
                scales: {
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        title: {
                            display: true,
                            text: 'Throughput (ops/s)'
                        }
                    },
                    y2: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        title: {
                            display: true,
                            text: 'Latency (ms/op)'
                        },
                        grid: {
                            drawOnChartArea: false
                        }
                    }
                }
            }
        };
    }

    /**
     * Creates configuration for the detailed chart.
     */
    createDetailedChartConfig(chartData) {
        return {
            type: 'radar',
            data: {
                labels: chartData.labels || [],
                datasets: [{
                    label: 'Performance Score',
                    data: (chartData.throughput || []).map(val => Math.min(100, val / 1000)),
                    borderColor: 'rgb(54, 162, 235)',
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    pointBackgroundColor: 'rgb(54, 162, 235)',
                    pointBorderColor: '#fff',
                    pointHoverBackgroundColor: '#fff',
                    pointHoverBorderColor: 'rgb(54, 162, 235)'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'Detailed Performance Analysis'
                    }
                },
                scales: {
                    r: {
                        beginAtZero: true,
                        max: 100,
                        ticks: {
                            stepSize: 20
                        }
                    }
                }
            }
        };
    }

    /**
     * Renders the trends table with benchmark comparisons.
     */
    async renderTrendsTable() {
        const data = await this.loadData();
        const benchmarks = data.benchmarks || [];
        const tableBody = document.getElementById('trends-table-body');
        
        if (!tableBody) return;

        tableBody.innerHTML = '';
        
        benchmarks.forEach(benchmark => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${benchmark.name}</td>
                <td>${benchmark.scoreFormatted}</td>
                <td>N/A</td>
                <td>-</td>
            `;
            tableBody.appendChild(row);
        });
    }

    /**
     * Updates all page elements based on the current page type.
     * @param {string} pageType - Type of page ('index', 'trends', 'detailed')
     */
    async updatePage(pageType = 'index') {
        try {
            switch (pageType) {
                case 'index':
                    await this.renderOverview();
                    await this.renderBenchmarkTable();
                    await this.renderChart('overview-chart', 'overview');
                    break;
                case 'trends':
                    await this.renderOverview();
                    await this.renderTrendsTable();
                    await this.renderChart('trends-chart', 'trends');
                    break;
                case 'detailed':
                    await this.renderOverview();
                    await this.renderBenchmarkTable();
                    await this.renderChart('detailed-chart', 'detailed');
                    break;
            }
        } catch (error) {
            console.error('Error updating page:', error);
        }
    }
}

// Create global instance
const benchmarkLoader = new BenchmarkDataLoader();

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        const pageType = document.body.dataset.pageType || 'index';
        benchmarkLoader.updatePage(pageType);
    });
} else {
    const pageType = document.body.dataset.pageType || 'index';
    benchmarkLoader.updatePage(pageType);
}