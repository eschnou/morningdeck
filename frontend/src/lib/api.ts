import type {
  SourceDTO,
  SourceStatus,
  CreateSourceRequest,
  UpdateSourceRequest,
  DayBriefDTO,
  DayBriefStatus,
  CreateDayBriefRequest,
  UpdateDayBriefRequest,
  DailyReportDTO,
  NewsItemDTO,
  ReadFilter,
} from '@/types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000';

export interface PublicConfig {
  emailVerificationEnabled: boolean;
  selfHostedMode: boolean;
}

export interface RegisterRequest {
  username: string;
  name: string;
  email: string;
  password: string;
  inviteCode?: string;
  language?: string;
}

export interface AuthRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  user: UserProfile;
}

export interface RegisterResponse {
  message: string;
  email: string;
}

export interface EmailVerificationResponse {
  message: string;
}

export interface UserProfile {
  id: string;
  username: string;
  name: string;
  email: string;
  language?: string;
  avatarUrl?: string;
  emailVerified?: boolean;
  role?: string;
}

export interface UpdateUserProfileDTO {
  name?: string;
  language?: string;
}

export interface ChangePasswordDTO {
  currentPassword: string;
  newPassword: string;
}

export interface AdminUserListItem {
  id: string;
  username: string;
  email: string;
  name: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string;
  subscriptionPlan: string;
  creditsBalance: number;
}

export interface AdminUserDetail {
  id: string;
  username: string;
  email: string;
  name: string;
  avatarUrl?: string;
  language?: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string;
  subscription: {
    id: string;
    plan: string;
    creditsBalance: number;
    monthlyCredits: number;
    nextRenewalDate: string;
    autoRenew: boolean;
  };
}

export interface PagedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
}

export interface AdminUpdateEnabledDTO {
  enabled: boolean;
}

export interface AdminUpdateEmailDTO {
  email: string;
}

export interface AdminResetPasswordDTO {
  newPassword: string;
}

export interface AdminUpdateSubscriptionDTO {
  plan: 'FREE' | 'PRO' | 'BUSINESS';
}

export interface AdminAdjustCreditsDTO {
  amount: number;
  mode: 'SET' | 'ADD';
}

export interface BulkUpdateResult {
  updatedCount: number;
}

class ApiClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor() {
    this.baseUrl = API_BASE_URL;
    this.token = localStorage.getItem('auth_token');
  }

  setToken(token: string | null) {
    this.token = token;
    if (token) {
      localStorage.setItem('auth_token', token);
    } else {
      localStorage.removeItem('auth_token');
    }
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }));
      throw new Error(error.message || 'API request failed');
    }

    // Handle empty responses (204 No Content or empty body)
    const contentLength = response.headers.get('content-length');
    if (response.status === 204 || contentLength === '0') {
      return undefined as T;
    }

    const text = await response.text();
    if (!text) {
      return undefined as T;
    }

    return JSON.parse(text);
  }

  // Public config endpoint
  async getPublicConfig(): Promise<PublicConfig> {
    return this.request('/public/config');
  }

  // Auth endpoints
  async register(data: RegisterRequest): Promise<RegisterResponse> {
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async verifyEmailWithToken(token: string): Promise<EmailVerificationResponse> {
    return this.request(`/auth/verify-email?token=${encodeURIComponent(token)}`);
  }

  async resendVerificationEmail(email: string): Promise<void> {
    return this.request('/auth/resend-verification', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  }

  async login(data: AuthRequest): Promise<AuthResponse> {
    const response = await this.request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    this.setToken(response.token);
    return response;
  }

  async logout() {
    this.setToken(null);
  }

  // Waitlist endpoints (public)
  async joinWaitlist(email: string): Promise<void> {
    return this.request('/waitlist/join', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  }

  async getWaitlistStats(): Promise<{ count: number }> {
    return this.request('/waitlist/stats');
  }

  // User endpoints
  async getCurrentUser(): Promise<UserProfile> {
    return this.request('/users/me');
  }

  async updateProfile(data: UpdateUserProfileDTO): Promise<UserProfile> {
    return this.request('/users/me', {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  }

  async changePassword(data: ChangePasswordDTO): Promise<void> {
    return this.request('/users/me/password', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  // Subscription endpoints
  async getSubscription(): Promise<{
    id: string;
    plan: string;
    creditsBalance: number;
    monthlyCredits: number;
    nextRenewalDate: string;
    autoRenew: boolean;
  }> {
    return this.request('/subscription');
  }

  // Admin endpoints
  async getAdminUsers(
    page = 0,
    size = 20,
    search?: string,
    enabled?: boolean,
    sortBy = 'createdAt',
    sortDir = 'desc'
  ): Promise<PagedResponse<AdminUserListItem>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy,
      sortDir,
    });
    if (search) params.append('search', search);
    if (enabled !== undefined) params.append('enabled', enabled.toString());
    return this.request(`/admin/users?${params.toString()}`);
  }

  async getAdminUserDetail(userId: string): Promise<AdminUserDetail> {
    return this.request(`/admin/users/${userId}`);
  }

  async updateUserEnabled(userId: string, data: AdminUpdateEnabledDTO): Promise<void> {
    return this.request(`/admin/users/${userId}/enabled`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async verifyUserEmail(userId: string): Promise<void> {
    return this.request(`/admin/users/${userId}/verify-email`, {
      method: 'PUT',
    });
  }

  async updateUserEmail(userId: string, data: AdminUpdateEmailDTO): Promise<void> {
    return this.request(`/admin/users/${userId}/email`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async resetUserPassword(userId: string, data: AdminResetPasswordDTO): Promise<void> {
    return this.request(`/admin/users/${userId}/password`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async updateUserSubscription(userId: string, data: AdminUpdateSubscriptionDTO): Promise<void> {
    return this.request(`/admin/users/${userId}/subscription`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async adjustUserCredits(userId: string, data: AdminAdjustCreditsDTO): Promise<void> {
    return this.request(`/admin/users/${userId}/credits`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  // Source endpoints
  async getSources(
    page = 0,
    size = 20,
    status?: SourceStatus,
    search?: string
  ): Promise<PagedResponse<SourceDTO>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    if (status) params.append('status', status);
    if (search) params.append('search', search);
    return this.request(`/sources?${params.toString()}`);
  }

  async getSource(id: string): Promise<SourceDTO> {
    return this.request(`/sources/${id}`);
  }

  async createSource(data: CreateSourceRequest): Promise<SourceDTO> {
    return this.request('/sources', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateSource(id: string, data: UpdateSourceRequest): Promise<SourceDTO> {
    return this.request(`/sources/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteSource(id: string): Promise<void> {
    return this.request(`/sources/${id}`, {
      method: 'DELETE',
    });
  }

  async refreshSource(id: string): Promise<SourceDTO> {
    return this.request(`/sources/${id}/refresh`, {
      method: 'POST',
    });
  }

  async markAllReadBySource(sourceId: string): Promise<BulkUpdateResult> {
    return this.request(`/sources/${sourceId}/mark-all-read`, {
      method: 'POST',
    });
  }

  // News item endpoints
  async getNewsItems(
    sourceId: string,
    page = 0,
    size = 20,
    readFilter?: ReadFilter,
    saved?: boolean
  ): Promise<PagedResponse<NewsItemDTO>> {
    const params = new URLSearchParams({
      sourceId,
      page: page.toString(),
      size: size.toString(),
    });
    if (readFilter && readFilter !== 'ALL') {
      params.append('readStatus', readFilter);
    }
    if (saved) {
      params.append('saved', 'true');
    }
    return this.request(`/news?${params.toString()}`);
  }

  async getNewsItem(itemId: string): Promise<NewsItemDTO> {
    return this.request(`/news/${itemId}`);
  }

  async toggleRead(itemId: string): Promise<NewsItemDTO> {
    return this.request(`/news/${itemId}/read`, { method: 'PATCH' });
  }

  async toggleSaved(itemId: string): Promise<NewsItemDTO> {
    return this.request(`/news/${itemId}/saved`, { method: 'PATCH' });
  }

  // DayBrief endpoints
  async getDayBriefs(
    page = 0,
    size = 20,
    status?: DayBriefStatus
  ): Promise<PagedResponse<DayBriefDTO>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    if (status) params.append('status', status);
    return this.request(`/daybriefs?${params.toString()}`);
  }

  async getDayBrief(id: string): Promise<DayBriefDTO> {
    return this.request(`/daybriefs/${id}`);
  }

  async createDayBrief(data: CreateDayBriefRequest): Promise<DayBriefDTO> {
    return this.request('/daybriefs', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateDayBrief(id: string, data: UpdateDayBriefRequest): Promise<DayBriefDTO> {
    return this.request(`/daybriefs/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteDayBrief(id: string): Promise<void> {
    return this.request(`/daybriefs/${id}`, {
      method: 'DELETE',
    });
  }

  async reorderBriefs(briefIds: string[]): Promise<void> {
    return this.request('/daybriefs/reorder', {
      method: 'POST',
      body: JSON.stringify({ briefIds }),
    });
  }

  async executeDayBrief(id: string): Promise<void> {
    return this.request(`/daybriefs/${id}/execute`, {
      method: 'POST',
    });
  }

  async markAllReadByBriefing(briefingId: string): Promise<BulkUpdateResult> {
    return this.request(`/daybriefs/${briefingId}/mark-all-read`, {
      method: 'POST',
    });
  }

  // Briefing-scoped source endpoints
  async getBriefingSources(
    briefingId: string,
    page = 0,
    size = 20,
    status?: SourceStatus
  ): Promise<PagedResponse<SourceDTO>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    if (status) params.append('status', status);
    return this.request(`/daybriefs/${briefingId}/sources?${params.toString()}`);
  }

  async createBriefingSource(
    briefingId: string,
    data: Omit<CreateSourceRequest, 'briefingId'>
  ): Promise<SourceDTO> {
    return this.request(`/daybriefs/${briefingId}/sources`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // Briefing-scoped items endpoints
  async getBriefingItems(
    briefingId: string,
    page = 0,
    size = 20,
    sourceId?: string,
    readFilter?: ReadFilter,
    saved?: boolean,
    minScore?: number,
    query?: string
  ): Promise<PagedResponse<NewsItemDTO>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    if (query && query.trim()) {
      params.append('q', query.trim());
    }
    if (sourceId) params.append('sourceId', sourceId);
    if (readFilter && readFilter !== 'ALL') {
      params.append('readStatus', readFilter);
    }
    if (saved) {
      params.append('saved', 'true');
    }
    if (minScore !== undefined) {
      params.append('minScore', minScore.toString());
    }
    return this.request(`/daybriefs/${briefingId}/items?${params.toString()}`);
  }

  // Report endpoints
  async getDayBriefReports(
    briefId: string,
    page = 0,
    size = 20
  ): Promise<PagedResponse<DailyReportDTO>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    return this.request(`/daybriefs/${briefId}/reports?${params.toString()}`);
  }

  async getDayBriefReport(briefId: string, reportId: string): Promise<DailyReportDTO> {
    return this.request(`/daybriefs/${briefId}/reports/${reportId}`);
  }

  async deleteDayBriefReport(briefId: string, reportId: string): Promise<void> {
    return this.request(`/daybriefs/${briefId}/reports/${reportId}`, {
      method: 'DELETE',
    });
  }

  async getLatestReports(): Promise<DailyReportDTO[]> {
    return this.request('/daybriefs/reports/latest');
  }
}

export const apiClient = new ApiClient();
