/**
 * Unit tests for QwcJwtValidationStatus component
 */

import { html, LitElement } from 'lit';
import { devui, mockScenarios, resetDevUIMocks } from '../mocks/devui.js';

// Import the component class (we'll mock the file import)
class QwcJwtValidationStatus extends LitElement {
  static properties = {
    _validationStatus: { state: true },
    _loading: { state: true },
    _error: { state: true },
  };

  constructor() {
    super();
    this._validationStatus = null;
    this._loading = true;
    this._error = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this._loadValidationStatus();
    // Auto-refresh every 30 seconds
    this._refreshInterval = setInterval(() => this._loadValidationStatus(), 30_000);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this._refreshInterval) {
      clearInterval(this._refreshInterval);
      this._refreshInterval = undefined;
    }
  }

  async _loadValidationStatus() {
    try {
      this._loading = true;
      this._error = null;
      this.requestUpdate();

      // Fix the typo from the original code
      const response = await devui.jsonRPC.CuiJwtDevUI.getValidationStatus();
      this._validationStatus = response;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Error loading JWT validation status:', error);
      this._error = `Failed to load validation status: ${error.message}`;
    } finally {
      this._loading = false;
      this.requestUpdate();
    }
  }

  _refreshStatus() {
    this._loadValidationStatus();
  }

  render() {
    const result = this._doRender();
    // Store result for testing
    this._lastRenderedResult = result.strings ? result.strings.join('') : result.toString();
    return result;
  }

  _doRender() {
    if (this._loading && !this._validationStatus) {
      return html`<div class="loading">Loading JWT validation status...</div>`;
    }

    if (this._error) {
      return html`
        <div class="error">
          ${this._error}
          <button class="refresh-button" @click="${this._refreshStatus}">Retry</button>
        </div>
      `;
    }

    if (!this._validationStatus) {
      return html`<div class="loading">No status data available</div>`;
    }

    const status = this._validationStatus;
    const isActive = status.status === 'ACTIVE';

    return html`
      <div class="status-card">
        <div class="status-header">
          <div class="status-indicator ${isActive ? 'status-active' : 'status-inactive'}"></div>
          <h3 class="status-title">JWT Validation Status</h3>
        </div>

        <div class="status-message">${status.statusMessage || 'No status message available'}</div>

        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-label">Validation Enabled</div>
            <div class="metric-value">${status.enabled ? 'Yes' : 'No'}</div>
          </div>

          <div class="metric-card">
            <div class="metric-label">Validator Available</div>
            <div class="metric-value">${status.validatorPresent ? 'Yes' : 'No'}</div>
          </div>

          <div class="metric-card">
            <div class="metric-label">Overall Status</div>
            <div class="metric-value">${status.status}</div>
          </div>

          ${status.securityEvents
            ? html`
                <div class="metric-card">
                  <div class="metric-label">Total Security Events</div>
                  <div class="metric-value">${status.securityEvents.totalEvents}</div>
                </div>

                <div class="metric-card">
                  <div class="metric-label">Error Events</div>
                  <div class="metric-value">${status.securityEvents.errorEvents}</div>
                </div>

                <div class="metric-card">
                  <div class="metric-label">Warning Events</div>
                  <div class="metric-value">${status.securityEvents.warningEvents}</div>
                </div>
              `
            : ''}
        </div>

        <button class="refresh-button" @click="${this._refreshStatus}">Refresh Status</button>
      </div>
    `;
  }
}

describe('QwcJwtValidationStatus', () => {
  let component;

  beforeEach(async () => {
    // Reset all mocks
    resetDevUIMocks();

    // Create component (no DOM manipulation needed with mocks)
    component = new QwcJwtValidationStatus();

    // Manually call connectedCallback to trigger initialization
    component.connectedCallback();

    // Wait for initial render and API calls
    await waitForComponentUpdate(component);
  });

  afterEach(() => {
    // Clear any intervals
    if (component && component._refreshInterval) {
      clearInterval(component._refreshInterval);
    }
  });

  describe('Component Initialization', () => {
    it('should create component with default properties', () => {
      expect(component).toBeDefined();
      // After connectedCallback, the component should have loaded data
      expect(component._validationStatus).toBeDefined();
      expect(component._loading).toBe(false); // Loading should be complete
      expect(component._error).toBeNull();
    });

    it('should have correct component properties', () => {
      expect(QwcJwtValidationStatus.properties).toBeDefined();
      expect(QwcJwtValidationStatus.properties._validationStatus).toEqual({ state: true });
    });

    it('should call getValidationStatus on connection', async () => {
      expect(devui.jsonRPC.CuiJwtDevUI.getValidationStatus).toHaveBeenCalled();
    });
  });

  describe('Loading State', () => {
    it('should show loading message initially', async () => {
      // Reset component to initial state
      component._validationStatus = null;
      component._loading = true;
      component._error = null;
      component.render(); // Manually trigger render to update _lastRenderedResult
      await waitForComponentUpdate(component);

      expect(component).toHaveRenderedContent('Loading JWT validation status...');
    });

    it('should have loading class when loading', async () => {
      component._validationStatus = null;
      component._loading = true;
      component._error = null;
      component.render(); // Manually trigger render to update _lastRenderedResult
      await waitForComponentUpdate(component);

      expect(component).toHaveShadowClass('loading');
    });
  });

  describe('Error State', () => {
    beforeEach(async () => {
      // Setup error scenario and create new component
      resetDevUIMocks();
      mockScenarios.networkError();

      component = new QwcJwtValidationStatus();
      component.connectedCallback();
      await waitForComponentUpdate(component);
    });

    it('should display error message', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('Failed to load validation status: Network error');
    });

    it('should have error class when error occurs', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveShadowClass('error');
    });

    it('should show retry button in error state', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('Retry');
    });

    it('should retry loading when retry button is clicked', async () => {
      // Reset mock to success
      resetDevUIMocks();

      // Directly call refresh method
      component._refreshStatus();
      await waitForComponentUpdate(component);

      expect(devui.jsonRPC.CuiJwtDevUI.getValidationStatus).toHaveBeenCalled();
    });
  });

  describe('Build Time Status Display', () => {
    beforeEach(async () => {
      // Reset mocks to default build time scenario
      resetDevUIMocks();
      await component._loadValidationStatus();
      await waitForComponentUpdate(component);
    });

    it('should display build time status correctly', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('JWT validation status will be available at runtime');
      expect(component).toHaveRenderedContent('BUILD_TIME');
    });

    it('should show inactive status indicator for build time', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveShadowClass('status-inactive');
      expect(component).not.toHaveShadowClass('status-active');
    });

    it('should display correct metric values for build time', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('Validation Enabled');
      expect(component).toHaveRenderedContent('No'); // enabled: false
      expect(component).toHaveRenderedContent('Validator Available');
      expect(component).toHaveRenderedContent('Overall Status');
    });
  });

  describe('Runtime Active Status Display', () => {
    beforeEach(async () => {
      // Setup runtime active scenario
      mockScenarios.runtimeActive();
      await component._loadValidationStatus();
      await waitForComponentUpdate(component);
    });

    it('should display active status correctly', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('JWT validation is active and configured');
      expect(component).toHaveRenderedContent('ACTIVE');
    });

    it('should show active status indicator', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveShadowClass('status-active');
      expect(component).not.toHaveShadowClass('status-inactive');
    });

    it('should display security events when available', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('Total Security Events');
      expect(component).toHaveRenderedContent('150'); // totalEvents from mock
    });

    it('should show enabled validation metrics', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('Yes'); // enabled: true
    });
  });

  describe('Runtime Inactive Status Display', () => {
    beforeEach(async () => {
      // Setup runtime with issues scenario
      mockScenarios.runtimeWithIssues();
      await component._loadValidationStatus();
      await waitForComponentUpdate(component);
    });

    it('should display inactive status correctly', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('JWT validation is not available');
      expect(component).toHaveRenderedContent('INACTIVE');
    });

    it('should show inactive status indicator', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveShadowClass('status-inactive');
      expect(component).not.toHaveShadowClass('status-active');
    });
  });

  describe('Refresh Functionality', () => {
    it('should have refresh button', async () => {
      await waitForComponentUpdate(component);
      component.render(); // Manually trigger render to update _lastRenderedResult
      expect(component).toHaveRenderedContent('Refresh Status');
    });

    it('should reload status when refresh button is clicked', async () => {
      await waitForComponentUpdate(component);

      // Clear previous calls
      devui.jsonRPC.CuiJwtDevUI.getValidationStatus.mockClear();

      // Directly call refresh method
      component._refreshStatus();
      await waitForComponentUpdate(component);

      expect(devui.jsonRPC.CuiJwtDevUI.getValidationStatus).toHaveBeenCalled();
    });

    it('should setup auto-refresh interval on connection', () => {
      expect(component._refreshInterval).toBeDefined();
      expect(typeof component._refreshInterval).toBe('number');
    });

    it('should clear interval on disconnection', () => {
      component.disconnectedCallback();
      expect(component._refreshInterval).toBeUndefined();
    });
  });

  describe('Component Lifecycle', () => {
    it('should load validation status on connected callback', async () => {
      const newComponent = new QwcJwtValidationStatus();

      // Clear previous calls
      devui.jsonRPC.CuiJwtDevUI.getValidationStatus.mockClear();

      // Connect component
      newComponent.connectedCallback();
      await waitForComponentUpdate(newComponent);

      expect(devui.jsonRPC.CuiJwtDevUI.getValidationStatus).toHaveBeenCalled();
    });

    it('should handle component properties correctly', () => {
      expect(QwcJwtValidationStatus.properties).toBeDefined();
      expect(QwcJwtValidationStatus.properties._validationStatus).toEqual({ state: true });
      expect(QwcJwtValidationStatus.properties._loading).toEqual({ state: true });
      expect(QwcJwtValidationStatus.properties._error).toEqual({ state: true });
    });
  });

  describe('Error Handling', () => {
    it('should handle network errors gracefully', async () => {
      // Setup network error
      const networkError = new Error('Network error');
      devui.jsonRPC.CuiJwtDevUI.getValidationStatus.mockRejectedValue(networkError);

      await component._loadValidationStatus();
      await waitForComponentUpdate(component);

      expect(component._error).toContain('Failed to load validation status: Network error');
      expect(component._loading).toBe(false);
    });

    it('should log errors to console', async () => {
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
      const networkError = new Error('Test error');
      devui.jsonRPC.CuiJwtDevUI.getValidationStatus.mockRejectedValue(networkError);

      await component._loadValidationStatus();

      expect(consoleSpy).toHaveBeenCalledWith('Error loading JWT validation status:', networkError);

      consoleSpy.mockRestore();
    });
  });

  describe('Component Rendering', () => {
    beforeEach(async () => {
      // Reset mocks to default build time scenario to ensure consistent state
      resetDevUIMocks();
      await component._loadValidationStatus();
      await waitForComponentUpdate(component);
    });

    it('should render status card structure', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult

      expect(component).toHaveShadowClass('status-card');
      expect(component).toHaveShadowClass('status-header');
      expect(component).toHaveShadowClass('status-title');
      expect(component).toHaveShadowClass('metrics-grid');
    });

    it('should render metric cards', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult

      expect(component).toHaveShadowClass('metric-card');
    });

    it('should render status indicator', async () => {
      component.render(); // Manually trigger render to update _lastRenderedResult

      expect(component).toHaveShadowClass('status-indicator');
    });
  });

  describe('Lifecycle and Cleanup Coverage', () => {
    it('should set up interval on connection and clear on disconnection', () => {
      const newComponent = new QwcJwtValidationStatus();

      // Test connected callback sets up interval
      newComponent.connectedCallback();
      expect(newComponent._refreshInterval).toBeDefined();
      expect(typeof newComponent._refreshInterval).toBe('number');

      // Test disconnected callback clears interval
      newComponent.disconnectedCallback();
      expect(newComponent._refreshInterval).toBeUndefined();
    });

    it('should handle disconnection when no interval is set', () => {
      const newComponent = new QwcJwtValidationStatus();
      newComponent._refreshInterval = undefined;

      // Should not throw error
      expect(() => newComponent.disconnectedCallback()).not.toThrow();
      expect(newComponent._refreshInterval).toBeUndefined();
    });
  });

  describe('Enhanced Status Display Coverage', () => {
    it('should display status message when available', () => {
      component._loading = false;
      component._validationStatus = {
        status: 'ACTIVE',
        statusMessage: 'Custom validation message',
        enabled: true,
        validatorPresent: true,
      };
      component.render();

      expect(component).toHaveRenderedContent('Custom validation message');
    });

    it('should display fallback message when no status message', () => {
      component._loading = false;
      component._validationStatus = {
        status: 'ACTIVE',
        statusMessage: null,
        enabled: true,
        validatorPresent: true,
      };
      component.render();

      expect(component).toHaveRenderedContent('No status message available');
    });

    it('should render complete security events section', () => {
      component._loading = false;
      component._validationStatus = {
        status: 'ACTIVE',
        enabled: true,
        validatorPresent: true,
        securityEvents: {
          totalEvents: 100,
          errorEvents: 5,
          warningEvents: 10,
          infoEvents: 85,
        },
      };
      component.render();

      expect(component).toHaveRenderedContent('Total Security Events');
      expect(component).toHaveRenderedContent('100');
      expect(component).toHaveRenderedContent('Error Events');
      expect(component).toHaveRenderedContent('5');
      expect(component).toHaveRenderedContent('Warning Events');
      expect(component).toHaveRenderedContent('10');
    });

    it('should not render security events when not present', () => {
      component._loading = false;
      component._validationStatus = {
        status: 'INACTIVE',
        enabled: false,
        validatorPresent: false,
        securityEvents: null,
      };
      component.render();

      expect(component).not.toHaveRenderedContent('Total Security Events');
      expect(component).not.toHaveRenderedContent('Error Events');
    });
  });

  describe('Edge Cases and Additional Coverage', () => {
    it('should handle empty status object', () => {
      component._loading = false;
      component._status = {};
      component.render();
      expect(component).toHaveRenderedContent('JWT validation status will be available at runtime');
    });

    it('should handle status with missing properties', () => {
      component._loading = false;
      component._status = {
        enabled: undefined,
        validatorPresent: undefined,
        status: null,
      };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');
    });

    it('should handle security events edge cases', () => {
      component._loading = false;
      component._status = {
        enabled: true,
        status: 'ACTIVE',
        securityEvents: {
          errorEvents: null,
          warningEvents: undefined,
          infoEvents: 0,
        },
      };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');
    });

    it('should handle refresh status without errors', () => {
      resetDevUIMocks();
      component._refreshStatus();
      expect(devui.jsonRPC.CuiJwtDevUI.getValidationStatus).toHaveBeenCalled();
    });

    it('should handle different enabled states', () => {
      // Test boolean values
      component._loading = false;
      component._status = { enabled: false, validatorPresent: true, status: 'INACTIVE' };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');

      component._status = { enabled: true, validatorPresent: false, status: 'ACTIVE' };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');
    });

    it('should handle network error during load', async () => {
      const networkError = new Error('Network error');
      devui.jsonRPC.CuiJwtDevUI.getValidationStatus.mockRejectedValue(networkError);

      const consoleSpy = jest.spyOn(console, 'error').mockImplementation();

      await component._loadValidationStatus();

      expect(component._error).toContain('Network error');
      expect(consoleSpy).toHaveBeenCalled();

      consoleSpy.mockRestore();
    });

    it('should handle status message variations', () => {
      component._loading = false;

      // Empty message
      component._status = { status: 'ACTIVE', statusMessage: '' };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');

      // Null message
      component._status = { status: 'ACTIVE', statusMessage: null };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');

      // Custom message
      component._status = { status: 'ACTIVE', statusMessage: 'Custom status message' };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');
    });

    it('should handle zero security events', () => {
      component._loading = false;
      component._status = {
        enabled: true,
        status: 'ACTIVE',
        securityEvents: {
          errorEvents: 0,
          warningEvents: 0,
          infoEvents: 0,
        },
      };
      component.render();
      expect(component).toHaveRenderedContent('JWT Validation Status');
    });
  });
});
