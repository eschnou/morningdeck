import { useState, useEffect } from 'react';
import { Navbar } from '@/components/Navbar';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import {
  Users,
  Search,
  MoreVertical,
  Shield,
  Mail,
  Key,
  CreditCard,
  Coins,
  UserCheck,
  UserX,
  Loader2,
} from 'lucide-react';
import {
  apiClient,
  AdminUserListItem,
  AdminUserDetail,
  PagedResponse,
} from '@/lib/api';
import { toast } from '@/hooks/use-toast';
import { format } from 'date-fns';

type AdminArea = 'users';

export default function Admin() {
  const [activeArea, setActiveArea] = useState<AdminArea>('users');
  const [users, setUsers] = useState<AdminUserListItem[]>([]);
  const [selectedUser, setSelectedUser] = useState<AdminUserDetail | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);

  // Dialog states
  const [showEmailDialog, setShowEmailDialog] = useState(false);
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [showSubscriptionDialog, setShowSubscriptionDialog] = useState(false);
  const [showCreditsDialog, setShowCreditsDialog] = useState(false);

  // Form states
  const [newEmail, setNewEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPlan, setNewPlan] = useState<'FREE' | 'PRO' | 'BUSINESS'>('FREE');
  const [creditsAmount, setCreditsAmount] = useState('');
  const [creditsMode, setCreditsMode] = useState<'SET' | 'ADD'>('ADD');

  const fetchUsers = async (page = 0, search = '') => {
    setIsLoading(true);
    try {
      const response: PagedResponse<AdminUserListItem> = await apiClient.getAdminUsers(
        page,
        20,
        search || undefined
      );
      setUsers(response.content);
      setTotalPages(response.totalPages);
      setCurrentPage(response.number);
    } catch (error: unknown) {
      toast({
        title: 'Error loading users',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const fetchUserDetail = async (userId: string) => {
    setIsLoadingDetail(true);
    try {
      const user = await apiClient.getAdminUserDetail(userId);
      setSelectedUser(user);
    } catch (error: unknown) {
      toast({
        title: 'Error loading user details',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    } finally {
      setIsLoadingDetail(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  useEffect(() => {
    const debounce = setTimeout(() => {
      fetchUsers(0, searchQuery);
    }, 300);
    return () => clearTimeout(debounce);
  }, [searchQuery]);

  const handleToggleEnabled = async () => {
    if (!selectedUser) return;
    try {
      await apiClient.updateUserEnabled(selectedUser.id, {
        enabled: !selectedUser.enabled,
      });
      toast({
        title: selectedUser.enabled ? 'User disabled' : 'User enabled',
        description: `${selectedUser.username} has been ${selectedUser.enabled ? 'disabled' : 'enabled'}.`,
      });
      fetchUserDetail(selectedUser.id);
      fetchUsers(currentPage, searchQuery);
    } catch (error: unknown) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
  };

  const handleVerifyEmail = async () => {
    if (!selectedUser) return;
    try {
      await apiClient.verifyUserEmail(selectedUser.id);
      toast({
        title: 'Email verified',
        description: `${selectedUser.email} has been verified.`,
      });
      fetchUserDetail(selectedUser.id);
      fetchUsers(currentPage, searchQuery);
    } catch (error: unknown) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
  };

  const handleUpdateEmail = async () => {
    if (!selectedUser || !newEmail) return;
    try {
      await apiClient.updateUserEmail(selectedUser.id, { email: newEmail });
      toast({
        title: 'Email updated',
        description: `Email changed to ${newEmail}.`,
      });
      setShowEmailDialog(false);
      setNewEmail('');
      fetchUserDetail(selectedUser.id);
      fetchUsers(currentPage, searchQuery);
    } catch (error: unknown) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
  };

  const handleResetPassword = async () => {
    if (!selectedUser || !newPassword) return;
    try {
      await apiClient.resetUserPassword(selectedUser.id, { newPassword });
      toast({
        title: 'Password reset',
        description: 'Password has been reset successfully.',
      });
      setShowPasswordDialog(false);
      setNewPassword('');
    } catch (error: unknown) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
  };

  const handleUpdateSubscription = async () => {
    if (!selectedUser) return;
    try {
      await apiClient.updateUserSubscription(selectedUser.id, { plan: newPlan });
      toast({
        title: 'Subscription updated',
        description: `Plan changed to ${newPlan}.`,
      });
      setShowSubscriptionDialog(false);
      fetchUserDetail(selectedUser.id);
      fetchUsers(currentPage, searchQuery);
    } catch (error: unknown) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
  };

  const handleAdjustCredits = async () => {
    if (!selectedUser || !creditsAmount) return;
    try {
      await apiClient.adjustUserCredits(selectedUser.id, {
        amount: parseInt(creditsAmount),
        mode: creditsMode,
      });
      toast({
        title: 'Credits adjusted',
        description: `Credits ${creditsMode === 'SET' ? 'set to' : 'adjusted by'} ${creditsAmount}.`,
      });
      setShowCreditsDialog(false);
      setCreditsAmount('');
      fetchUserDetail(selectedUser.id);
      fetchUsers(currentPage, searchQuery);
    } catch (error: unknown) {
      toast({
        title: 'Error',
        description: error instanceof Error ? error.message : 'An error occurred',
        variant: 'destructive',
      });
    }
  };

  const getInitials = (name: string) => {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);
  };

  return (
    <div className="min-h-screen bg-gradient-subtle">
      <Navbar />
      <div className="flex h-[calc(100vh-4rem)]">
        {/* Left Sidebar - Admin Areas */}
        <div className="w-64 border-r bg-card p-4">
          <div className="flex items-center gap-2 mb-6">
            <Shield className="h-5 w-5 text-primary" />
            <h2 className="font-semibold">Admin Panel</h2>
          </div>
          <nav className="space-y-1">
            <Button
              variant={activeArea === 'users' ? 'secondary' : 'ghost'}
              className="w-full justify-start"
              onClick={() => setActiveArea('users')}
            >
              <Users className="mr-2 h-4 w-4" />
              User Management
            </Button>
          </nav>
        </div>

        {/* Main Content Area */}
        <div className="flex-1 flex flex-row">
          {/* Left - User List */}
          <div className="w-96 border-r p-4 overflow-hidden flex flex-col">
            <div className="flex items-center gap-4 mb-4">
              <h3 className="text-lg font-semibold">Users</h3>
              <div className="flex-1 max-w-sm">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search users..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9"
                  />
                </div>
              </div>
            </div>

            <ScrollArea className="flex-1">
              {isLoading ? (
                <div className="flex items-center justify-center h-32">
                  <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                </div>
              ) : users.length === 0 ? (
                <div className="text-center text-muted-foreground py-8">
                  No users found
                </div>
              ) : (
                <div className="space-y-2">
                  {users.map((user) => (
                    <Card
                      key={user.id}
                      className={`cursor-pointer transition-colors hover:bg-muted ${
                        selectedUser?.id === user.id ? 'bg-muted border-primary' : ''
                      }`}
                      onClick={() => fetchUserDetail(user.id)}
                    >
                      <CardContent className="p-3">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-3">
                            <Avatar className="h-8 w-8">
                              <AvatarFallback className="text-xs">
                                {getInitials(user.name || user.username)}
                              </AvatarFallback>
                            </Avatar>
                            <div>
                              <div className="font-medium text-sm">{user.name}</div>
                              <div className="text-xs text-muted-foreground">
                                @{user.username} - {user.email}
                              </div>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            {user.role === 'ADMIN' && (
                              <Badge variant="secondary" className="text-xs">
                                Admin
                              </Badge>
                            )}
                            <Badge
                              variant={user.enabled ? 'default' : 'destructive'}
                              className="text-xs"
                            >
                              {user.enabled ? 'Active' : 'Disabled'}
                            </Badge>
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              )}
            </ScrollArea>

            {totalPages > 1 && (
              <div className="flex justify-center gap-2 mt-4">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => fetchUsers(currentPage - 1, searchQuery)}
                  disabled={currentPage === 0}
                >
                  Previous
                </Button>
                <span className="flex items-center text-sm text-muted-foreground">
                  Page {currentPage + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => fetchUsers(currentPage + 1, searchQuery)}
                  disabled={currentPage >= totalPages - 1}
                >
                  Next
                </Button>
              </div>
            )}
          </div>

          {/* Right - User Details */}
          <div className="flex-1 p-4 overflow-auto">
            {isLoadingDetail ? (
              <div className="flex items-center justify-center h-full">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : selectedUser ? (
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <Avatar className="h-12 w-12">
                        <AvatarImage src={selectedUser.avatarUrl} />
                        <AvatarFallback>
                          {getInitials(selectedUser.name || selectedUser.username)}
                        </AvatarFallback>
                      </Avatar>
                      <div>
                        <CardTitle className="text-lg">{selectedUser.name}</CardTitle>
                        <div className="text-sm text-muted-foreground">
                          @{selectedUser.username}
                        </div>
                      </div>
                    </div>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="outline" size="icon">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={handleToggleEnabled}>
                          {selectedUser.enabled ? (
                            <>
                              <UserX className="mr-2 h-4 w-4" />
                              Disable User
                            </>
                          ) : (
                            <>
                              <UserCheck className="mr-2 h-4 w-4" />
                              Enable User
                            </>
                          )}
                        </DropdownMenuItem>
                        {!selectedUser.emailVerified && (
                          <DropdownMenuItem onClick={handleVerifyEmail}>
                            <Mail className="mr-2 h-4 w-4" />
                            Verify Email
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onClick={() => setShowEmailDialog(true)}>
                          <Mail className="mr-2 h-4 w-4" />
                          Change Email
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => setShowPasswordDialog(true)}>
                          <Key className="mr-2 h-4 w-4" />
                          Reset Password
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          onClick={() => {
                            setNewPlan((selectedUser.subscription?.plan as 'FREE' | 'PRO' | 'BUSINESS') || 'FREE');
                            setShowSubscriptionDialog(true);
                          }}
                        >
                          <CreditCard className="mr-2 h-4 w-4" />
                          Change Subscription
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => setShowCreditsDialog(true)}>
                          <Coins className="mr-2 h-4 w-4" />
                          Adjust Credits
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <Label className="text-xs text-muted-foreground">Email</Label>
                      <div className="text-sm flex items-center gap-2">
                        {selectedUser.email}
                        {selectedUser.emailVerified ? (
                          <Badge variant="outline" className="text-xs">
                            Verified
                          </Badge>
                        ) : (
                          <Badge variant="destructive" className="text-xs">
                            Unverified
                          </Badge>
                        )}
                      </div>
                    </div>
                    <div>
                      <Label className="text-xs text-muted-foreground">Status</Label>
                      <div className="text-sm">
                        <Badge variant={selectedUser.enabled ? 'default' : 'destructive'}>
                          {selectedUser.enabled ? 'Active' : 'Disabled'}
                        </Badge>
                      </div>
                    </div>
                    <div>
                      <Label className="text-xs text-muted-foreground">Role</Label>
                      <div className="text-sm">{selectedUser.role}</div>
                    </div>
                    <div>
                      <Label className="text-xs text-muted-foreground">Language</Label>
                      <div className="text-sm">{selectedUser.language || 'Not set'}</div>
                    </div>
                    <div>
                      <Label className="text-xs text-muted-foreground">Created</Label>
                      <div className="text-sm">
                        {format(new Date(selectedUser.createdAt), 'PPP')}
                      </div>
                    </div>
                  </div>

                  {selectedUser.subscription && (
                    <>
                      <Separator className="my-4" />
                      <div>
                        <Label className="text-xs text-muted-foreground mb-2 block">
                          Subscription
                        </Label>
                        <div className="grid grid-cols-2 gap-4">
                          <div>
                            <Label className="text-xs text-muted-foreground">Plan</Label>
                            <div className="text-sm font-medium">
                              {selectedUser.subscription.plan}
                            </div>
                          </div>
                          <div>
                            <Label className="text-xs text-muted-foreground">Credits</Label>
                            <div className="text-sm">
                              {selectedUser.subscription.creditsBalance} /{' '}
                              {selectedUser.subscription.monthlyCredits}
                            </div>
                          </div>
                          <div>
                            <Label className="text-xs text-muted-foreground">
                              Next Renewal
                            </Label>
                            <div className="text-sm">
                              {format(
                                new Date(selectedUser.subscription.nextRenewalDate),
                                'PPP'
                              )}
                            </div>
                          </div>
                          <div>
                            <Label className="text-xs text-muted-foreground">
                              Auto Renew
                            </Label>
                            <div className="text-sm">
                              {selectedUser.subscription.autoRenew ? 'Yes' : 'No'}
                            </div>
                          </div>
                        </div>
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>
            ) : (
              <div className="flex items-center justify-center h-full text-muted-foreground">
                Select a user to view details
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Email Dialog */}
      <Dialog open={showEmailDialog} onOpenChange={setShowEmailDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change Email</DialogTitle>
            <DialogDescription>
              Update the email address for {selectedUser?.username}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="email">New Email</Label>
            <Input
              id="email"
              type="email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              placeholder="Enter new email"
              className="mt-2"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowEmailDialog(false)}>
              Cancel
            </Button>
            <Button onClick={handleUpdateEmail}>Update Email</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Password Dialog */}
      <Dialog open={showPasswordDialog} onOpenChange={setShowPasswordDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reset Password</DialogTitle>
            <DialogDescription>
              Set a new password for {selectedUser?.username}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="password">New Password</Label>
            <Input
              id="password"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Enter new password (min 8 characters)"
              className="mt-2"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowPasswordDialog(false)}>
              Cancel
            </Button>
            <Button onClick={handleResetPassword}>Reset Password</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Subscription Dialog */}
      <Dialog open={showSubscriptionDialog} onOpenChange={setShowSubscriptionDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change Subscription</DialogTitle>
            <DialogDescription>
              Update the subscription plan for {selectedUser?.username}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label>Plan</Label>
            <Select value={newPlan} onValueChange={(v) => setNewPlan(v as 'FREE' | 'PRO' | 'BUSINESS')}>
              <SelectTrigger className="mt-2">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FREE">Free</SelectItem>
                <SelectItem value="PRO">Pro</SelectItem>
                <SelectItem value="BUSINESS">Business</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowSubscriptionDialog(false)}>
              Cancel
            </Button>
            <Button onClick={handleUpdateSubscription}>Update Plan</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Credits Dialog */}
      <Dialog open={showCreditsDialog} onOpenChange={setShowCreditsDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Adjust Credits</DialogTitle>
            <DialogDescription>
              Modify credits for {selectedUser?.username}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4 space-y-4">
            <div>
              <Label>Mode</Label>
              <Select value={creditsMode} onValueChange={(v) => setCreditsMode(v as 'SET' | 'ADD')}>
                <SelectTrigger className="mt-2">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADD">Add to current</SelectItem>
                  <SelectItem value="SET">Set to value</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label htmlFor="credits">Amount</Label>
              <Input
                id="credits"
                type="number"
                value={creditsAmount}
                onChange={(e) => setCreditsAmount(e.target.value)}
                placeholder="Enter amount"
                className="mt-2"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreditsDialog(false)}>
              Cancel
            </Button>
            <Button onClick={handleAdjustCredits}>Adjust Credits</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
