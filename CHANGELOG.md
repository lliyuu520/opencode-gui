# Changelog

All notable changes to this project will be documented in this file.

## [0.1.0] - 2024-01-01

### Added
- Initial release of OpenCode IntelliJ Plugin
- Main ToolWindow with chat interface
- Message history with styled bubbles
- File and image upload support
- Sidebar with tabs for Sessions, MCP Servers, and Skills
- Settings page for API configuration
- OpenCode CLI service for process management
- Auto-start server on project open option

### Features
- **Chat Panel**: Send messages to OpenCode AI assistant
- **File Upload**: Attach files and images to messages
- **Session Management**: View and manage conversation sessions
- **MCP Servers**: Monitor connected MCP servers
- **Skills/Agents**: Select agent (build/plan) and view available skills
- **Settings**: Configure OpenCode path, default model, API keys

### Technical
- Built with Kotlin and IntelliJ Platform Plugin SDK
- Uses Swing for UI components
- Coroutines for async operations
- Persistent settings storage