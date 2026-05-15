import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';
import 'package:ecoskiller_mobile_app/features/auth/presentation/pages/login_page.dart';
import 'package:ecoskiller_mobile_app/features/auth/data/providers/auth_service.dart';
import 'package:ecoskiller_mobile_app/features/shark_tank/presentation/pages/corporate_shark_tank_page.dart';

class CorporateDashboardPage extends StatelessWidget {
  final Map<String, dynamic> user;

  const CorporateDashboardPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildSliverAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  FadeInDown(
                    child: _buildHeaderCard(),
                  ),
                  const SizedBox(height: 24),
                  _buildSectionTitle('CORPORATE STRATEGY NODE'),
                  const SizedBox(height: 16),
                  _buildStatsRow(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('PARTNERSHIPS & DEALS'),
                  const SizedBox(height: 16),
                  _buildDealsList(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('INNOVATION NODES'),
                  const SizedBox(height: 16),
                  _buildSharkTankCard(context),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSliverAppBar(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;
    return SliverAppBar(
      expandedHeight: 120,
      pinned: true,
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      actions: [
        IconButton(
          icon: const Icon(LucideIcons.logOut, color: Colors.redAccent),
          onPressed: () async {
            await AuthService().logout();
            if (context.mounted) {
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (context) => const LoginPage()),
                (route) => false,
              );
            }
          },
        ),
      ],
      flexibleSpace: FlexibleSpaceBar(
        title: Text(
          'CORPORATE NODE',
          style: GoogleFonts.inter(
            color: isDark ? AppTheme.crispText : AppTheme.deepOcean,
            fontWeight: FontWeight.w900,
            fontSize: 16,
            letterSpacing: 2,
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderCard() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF2c3e50), Color(0xFF4ca1af)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Corporate: ${user['name']}',
            style: GoogleFonts.inter(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'Scaling operations & impact',
            style: GoogleFonts.inter(color: Colors.white70, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 1.5,
      ),
    );
  }

  Widget _buildStatsRow() {
    return Row(
      children: [
        _buildStatCard('Partners', '12', Colors.blueGrey),
        const SizedBox(width: 16),
        _buildStatCard('Budget', '₹5.2M', Colors.cyan),
      ],
    );
  }

  Widget _buildStatCard(String label, String value, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          children: [
            Text(value, style: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
            Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey)),
          ],
        ),
      ),
    );
  }

  Widget _buildDealsList() {
    return Column(
      children: [
        _buildDealItem('Tech Park Acquisition', 'Under Review', Colors.orange),
        const SizedBox(height: 12),
        _buildDealItem('Skill Grant Program', 'Approved', Colors.green),
      ],
    );
  }

  Widget _buildDealItem(String title, String status, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Icon(LucideIcons.briefcase, color: color, size: 24),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
              Text(status, style: GoogleFonts.inter(fontSize: 12, color: color)),
            ],
          ),
          const Spacer(),
          const Icon(LucideIcons.chevronRight, color: Colors.grey),
        ],
      ),
    );
  }

  Widget _buildSharkTankCard(BuildContext context) {
    return GestureDetector(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => CorporateSharkTankPage(user: user),
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF232526), Color(0xFF414345)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.2),
              blurRadius: 15,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.waves, color: Colors.amber, size: 28),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'CORPORATE SCOUT',
                    style: GoogleFonts.inter(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                      letterSpacing: 1.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Flag and acquire top innovations',
                    style: GoogleFonts.inter(color: Colors.white70, fontSize: 12),
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight, color: Colors.white54),
          ],
        ),
      ),
    );
  }
}
