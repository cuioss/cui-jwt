// CUI JWT Benchmarking - Shared Navigation Component

class BenchmarkNavigation {
  constructor(currentPage = '') {
    this.currentPage = currentPage;
    this.pages = [
      { id: 'micro', title: 'Micro Benchmarks', file: 'index.html', description: 'JMH unit-level performance testing' },
      { id: 'integration', title: 'Integration Tests', file: 'integration.html', description: 'End-to-end containerized testing' },
      { id: 'step-metrics', title: 'Step Metrics', file: 'step-metrics.html', description: 'Detailed step-by-step analysis' },
      { id: 'trends', title: 'Performance Trends', file: 'trends.html', description: 'Historical performance tracking' }
    ];
  }

  // Generate navigation HTML
  generateNavigation() {
    return this.pages.map(page => {
      const isCurrent = page.id === this.currentPage;
      const badgeClass = isCurrent ? 'badge-current' : 'badge-primary';
      const title = isCurrent ? `Current: ${page.description}` : page.description;
      
      return `<a href="${page.file}" class="badge ${badgeClass}" title="${title}">
        ${page.title}${isCurrent ? ' (Current)' : ''}
      </a>`;
    }).join('\n      ');
  }

  // Inject navigation into header
  injectNavigation() {
    const headerElement = document.querySelector('.header');
    if (headerElement) {
      const existingNav = headerElement.querySelector('.navigation');
      if (existingNav) {
        existingNav.innerHTML = this.generateNavigation();
      } else {
        const navDiv = document.createElement('div');
        navDiv.className = 'navigation';
        navDiv.innerHTML = this.generateNavigation();
        headerElement.appendChild(navDiv);
      }
    }
  }

  // Initialize navigation when DOM is loaded
  init() {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => this.injectNavigation());
    } else {
      this.injectNavigation();
    }
  }
}

// Utility function to get current page type from filename or path
function getCurrentPageType() {
  const path = window.location.pathname;
  const filename = path.split('/').pop() || 'index.html';
  
  if (filename.includes('integration')) return 'integration';
  if (filename.includes('step-metrics')) return 'step-metrics';  
  if (filename.includes('trends')) return 'trends';
  if (filename === 'index.html' || filename === '') return 'micro';
  return 'micro'; // default
}

// Auto-initialize navigation
const currentPage = getCurrentPageType();
const navigation = new BenchmarkNavigation(currentPage);
navigation.init();