import { html, LitElement } from 'lit';
import { devui, resetDevUIMocks } from '../mocks/devui.js';

// Mock notificationStore
const notificationStore = {
  addNotification: jest.fn(),
};

class QwcJwtConfig extends LitElement {
  static get properties() {
    return {
      _loading: { type: Boolean, state: true },
      _error: { type: String, state: true },
      _configuration: { type: Object, state: true },
      _lastRenderedResult: { type: String, state: true },
    };
  }

  constructor() {
    super();
    this._configuration = null;
    this._healthInfo = null;
    this._loading = true;
    this._error = null;
    this._lastRenderedResult = '';
  }

  connectedCallback() {
    super.connectedCallback();
    this._loadConfiguration();
  }

  createRenderRoot() {
    return this;
  }

  render() {
    const result = this._doRender();
    this._lastRenderedResult = result.strings ? result.strings.join('') : result.toString();
    return result;
  }

  _doRender() {
    const loadingOrErrorContent = this._renderLoadingOrError();
    if (loadingOrErrorContent) {
      return loadingOrErrorContent;
    }

    if (!this._configuration) {
      return html`<div class="loading">No configuration data available</div>`;
    }

    return this._renderConfiguration();
  }

  _renderLoadingOrError() {
    if (this._loading && !this._configuration) {
      return html`<div class="loading">Loading JWT configuration...</div>`;
    }

    if (this._error) {
      return html`
        <div class="error">
          ${this._error}
          <button class="refresh-button" @click="${this._refreshConfiguration}">Retry</button>
        </div>
      `;
    }

    return null;
  }

  _renderConfiguration() {
    const config = this._configuration;

    return html`
      <div class="config-container">
        <div class="config-header">
          <h3 class="config-title">JWT Configuration</h3>
          <button class="refresh-button" @click="${this._refreshConfiguration}">Refresh</button>
        </div>

        <div class="config-sections">
          ${this._renderGeneralSection(config)} ${this._renderParserSection(config)}
          ${this._renderHealthSection(config)} ${this._renderIssuersSection(config)}
          ${this._renderMessageSection(config)}
        </div>
      </div>
    `;
  }

  _renderGeneralSection(config) {
    return html`
      <div class="config-section">
        <h4 class="section-title">General Configuration</h4>
        <div class="config-grid">
          <div class="config-item">
            <div class="config-label">JWT Validation Enabled</div>
            <div class="config-value ${config.enabled ? 'enabled' : 'disabled'}">
              ${config.enabled ? 'Yes' : 'No'}
            </div>
          </div>

          <div class="config-item">
            <div class="config-label">Health Checks Enabled</div>
            <div class="config-value ${config.healthEnabled ? 'enabled' : 'disabled'}">
              ${config.healthEnabled ? 'Yes' : 'No'}
            </div>
          </div>

          ${config.buildTime
            ? html`
                <div class="config-item">
                  <div class="config-label">Build Time</div>
                  <div class="config-value build-time">Yes</div>
                </div>
              `
            : ''}
        </div>
      </div>
    `;
  }

  _renderParserSection(config) {
    if (!config.parser) {
      return '';
    }

    return html`
      <div class="config-section">
        <h4 class="section-title">Parser Configuration</h4>
        <div class="config-grid">
          <div class="config-item">
            <div class="config-label">Max Token Size</div>
            <div class="config-value">${config.parser.maxTokenSizeBytes} bytes</div>
          </div>

          <div class="config-item">
            <div class="config-label">Clock Leeway</div>
            <div class="config-value">${config.parser.leewaySeconds} seconds</div>
          </div>

          <div class="config-item">
            <div class="config-label">Validate Expiration</div>
            <div class="config-value">${config.parser.validateExpiration ? 'Yes' : 'No'}</div>
          </div>

          <div class="config-item">
            <div class="config-label">Allowed Algorithms</div>
            <div class="config-value algorithms">${config.parser.allowedAlgorithms}</div>
          </div>
        </div>
      </div>
    `;
  }

  _renderHealthSection(config) {
    if (!config.health) {
      return '';
    }

    return html`
      <div class="config-section">
        <h4 class="section-title">Health Check Configuration</h4>
        <div class="config-grid">
          <div class="config-item">
            <div class="config-label">Health Checks Enabled</div>
            <div class="config-value">${config.health.enabled ? 'Yes' : 'No'}</div>
          </div>

          ${this._renderHealthJwksSection(config)}
        </div>
      </div>
    `;
  }

  _renderHealthJwksSection(config) {
    if (!config.health.jwks) {
      return '';
    }

    return html`
      <div class="config-item">
        <div class="config-label">JWKS Health Cache</div>
        <div class="config-value">${config.health.jwks.cacheSeconds} seconds</div>
      </div>

      <div class="config-item">
        <div class="config-label">JWKS Health Timeout</div>
        <div class="config-value">${config.health.jwks.timeoutSeconds} seconds</div>
      </div>
    `;
  }

  _renderIssuersSection(config) {
    if (!config.issuers) {
      return '';
    }

    return html`
      <div class="config-section">
        <h4 class="section-title">Issuer Configuration</h4>
        ${Object.keys(config.issuers).length > 0
          ? this._renderIssuersGrid(config.issuers)
          : html`<div class="no-issuers">No issuers configured</div>`}
      </div>
    `;
  }

  _renderIssuersGrid(issuers) {
    return html`
      <div class="issuers-grid">
        ${Object.entries(issuers).map(([name, issuer]) => this._renderIssuerCard(name, issuer))}
      </div>
    `;
  }

  _renderIssuerCard(name, issuer) {
    return html`
      <div class="issuer-card">
        <div class="issuer-name">${name}</div>
        <div class="issuer-details">
          <div class="config-item">
            <div class="config-label">URL</div>
            <div class="config-value">${issuer.url}</div>
          </div>
          <div class="config-item">
            <div class="config-label">Enabled</div>
            <div class="config-value">${issuer.enabled ? 'Yes' : 'No'}</div>
          </div>
        </div>
      </div>
    `;
  }

  _renderMessageSection(config) {
    if (!config.message) {
      return '';
    }

    return html`
      <div class="config-section info-section">
        <div class="info-message">${config.message}</div>
      </div>
    `;
  }

  async _loadConfiguration() {
    try {
      this._loading = true;
      this._error = null;

      const [config, health] = await Promise.all([
        devui.jsonRPC.CuiJwtDevUI.getConfiguration(),
        devui.jsonRPC.CuiJwtDevUI.getHealthInfo(),
      ]);

      this._configuration = config;
      this._healthInfo = health;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Error loading JWT configuration:', error);
      this._error = `Failed to load configuration: ${error.message}`;
      notificationStore.addNotification({
        type: 'error',
        message: this._error,
      });
    } finally {
      this._loading = false;
    }
  }

  async _refreshConfiguration() {
    await this._loadConfiguration();
  }
}

customElements.define('qwc-jwt-config', QwcJwtConfig);

// Test cases for QwcJwtConfig component
describe('QwcJwtConfig', () => {
  let component;

  beforeEach(() => {
    // Reset DevUI mocks before each test
    resetDevUIMocks();

    // Create a new component instance for each test
    component = new QwcJwtConfig();
  });

  afterEach(() => {
    // Clean up mocks
    jest.clearAllMocks();
  });

  describe('Component Initialization', () => {
    test('should initialize with default properties', () => {
      expect(component._loading).toBe(true);
      expect(component._error).toBe(null);
      expect(component._configuration).toBe(null);
      expect(component._healthInfo).toBe(null);
      expect(component._lastRenderedResult).toBe('');
    });

    test('should have correct static properties', () => {
      const properties = QwcJwtConfig.properties;
      expect(properties._loading).toEqual({ type: Boolean, state: true });
      expect(properties._error).toEqual({ type: String, state: true });
      expect(properties._configuration).toEqual({ type: Object, state: true });
      expect(properties._lastRenderedResult).toEqual({ type: String, state: true });
    });
  });

  describe('Rendering', () => {
    test('should render loading state when loading and no configuration', () => {
      component._loading = true;
      component._configuration = null;

      component.render();
      expect(component._lastRenderedResult).toContain('Loading JWT configuration...');
    });

    test('should render error state when error exists', () => {
      component._loading = false;
      component._error = 'Test error message';

      component.render();
      expect(component._lastRenderedResult).toContain('Test error message');
      expect(component._lastRenderedResult).toContain('Retry');
    });

    test('should render no configuration message when no data available', () => {
      component._loading = false;
      component._error = null;
      component._configuration = null;

      component.render();
      expect(component._lastRenderedResult).toContain('No configuration data available');
    });

    test('should render configuration when data is available', () => {
      component._configuration = {
        enabled: true,
        healthEnabled: true,
        parser: {
          maxTokenSizeBytes: 1024,
          leewaySeconds: 30,
          validateExpiration: true,
          allowedAlgorithms: 'RS256',
        },
      };

      component.render();
      expect(component._lastRenderedResult).toContain('JWT Configuration');
      expect(component._lastRenderedResult).toContain('General Configuration');
    });
  });

  describe('Configuration Loading', () => {
    test('should handle successful configuration loading', async () => {
      const mockConfig = { enabled: true, healthEnabled: false };
      const mockHealth = { configurationValid: true, overallStatus: 'OK' };

      devui.jsonRPC.CuiJwtDevUI.getConfiguration.mockResolvedValueOnce(mockConfig);
      devui.jsonRPC.CuiJwtDevUI.getHealthInfo.mockResolvedValueOnce(mockHealth);

      await component._loadConfiguration();

      expect(component._loading).toBe(false);
      expect(component._error).toBe(null);
      expect(component._configuration).toEqual(mockConfig);
      expect(component._healthInfo).toEqual(mockHealth);
      expect(devui.jsonRPC.CuiJwtDevUI.getConfiguration).toHaveBeenCalledTimes(1);
      expect(devui.jsonRPC.CuiJwtDevUI.getHealthInfo).toHaveBeenCalledTimes(1);
    });

    test('should handle failed configuration loading', async () => {
      const error = new Error('Network error');
      devui.jsonRPC.CuiJwtDevUI.getConfiguration.mockRejectedValueOnce(error);
      devui.jsonRPC.CuiJwtDevUI.getHealthInfo.mockRejectedValueOnce(error);

      await component._loadConfiguration();

      expect(component._loading).toBe(false);
      expect(component._error).toContain('Failed to load configuration: Network error');
      expect(component._configuration).toBe(null);
      expect(component._healthInfo).toBe(null);
    });

    test('should handle partial failure in configuration loading', async () => {
      const mockConfig = { enabled: true, healthEnabled: false };
      const error = new Error('Health service error');

      devui.jsonRPC.CuiJwtDevUI.getConfiguration.mockResolvedValueOnce(mockConfig);
      devui.jsonRPC.CuiJwtDevUI.getHealthInfo.mockRejectedValueOnce(error);

      await component._loadConfiguration();

      expect(component._loading).toBe(false);
      expect(component._error).toContain('Failed to load configuration: Health service error');
      expect(component._configuration).toBe(null);
      expect(component._healthInfo).toBe(null);
    });
  });
});
