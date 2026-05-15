import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';
import 'package:ecoskiller_mobile_app/features/auth/presentation/pages/login_page.dart';
import 'package:ecoskiller_mobile_app/features/auth/data/providers/auth_service.dart';
import 'package:ecoskiller_mobile_app/features/shark_tank/presentation/pages/institute_shark_tank_page.dart';

class InstituteDashboardPage extends StatelessWidget {
  final Map<String, dynamic> user;

  const InstituteDashboardPage({super.key, required this.user});

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
                  _buildSectionTitle('INSTITUTIONAL PERFORMANCE'),
                  const SizedBox(height: 16),
                  _buildStatsRow(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('CAMPUS STATUS'),
                  const SizedBox(height: 16),
                  _buildCampusStatus(),
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
          'INSTITUTE NODE',
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
          colors: [Color(0xFF1D2671), Color(0xFFC33764)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Institute: ${user['name']}',
            style: GoogleFonts.inter(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'Empowering the future workforce',
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
        _buildStatCard('Students', '1,200', Colors.indigo),
        const SizedBox(width: 16),
        _buildStatCard('Placements', '85%', Colors.deepOrange),
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

  Widget _buildCampusStatus() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        children: [
          _buildStatusLine('Active Projects', '42', Colors.blue),
          const Divider(),
          _buildStatusLine('Skills Certified', '850', Colors.green),
          const Divider(),
          _buildStatusLine('Research Papers', '12', Colors.orange),
        ],
      ),
    );
  }

  Widget _buildStatusLine(String label, String val, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w500)),
          Text(val, style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.bold, color: color)),
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
            builder: (context) => InstituteSharkTankPage(user: user),
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF1D2671), Color(0xFFC33764)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.pink.withOpacity(0.2),
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
              child: const Icon(LucideIcons.university, color: Colors.white, size: 28),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'INNOVATION NODE',
                    style: GoogleFonts.inter(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                      letterSpacing: 1.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Oversee multi-campus shark tank rounds',
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
